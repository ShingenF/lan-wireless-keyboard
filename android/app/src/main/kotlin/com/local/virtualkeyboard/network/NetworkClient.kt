package com.local.virtualkeyboard.network

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.local.virtualkeyboard.protocol.AuthenticationProof
import com.local.virtualkeyboard.protocol.OutgoingCommand
import com.local.virtualkeyboard.protocol.ProtocolCodec
import com.local.virtualkeyboard.settings.ConnectionSettings
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

private fun fingerprintOf(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString("") { "%02X".format(it.toInt() and 0xff) }

class NetworkClient(
    private val settings: ConnectionSettings,
    private val listener: Listener,
) : Closeable {
    enum class State { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED }

    interface Listener {
        fun onStateChanged(state: State)
        fun onPinLearned(fingerprint: String)
        fun onConnectionError(message: String)
    }

    private data class Session(
        val socket: SSLSocket,
        val writer: OutputStreamWriter,
    ) : Closeable {
        val writeLock = Any()
        override fun close() = socket.close()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = ScheduledThreadPoolExecutor(2).apply {
        removeOnCancelPolicy = true
    }
    private val running = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private val sequence = AtomicLong(0)
    private val pendingCompletions = ConcurrentHashMap<Long, (Boolean) -> Unit>()

    @Volatile private var currentSession: Session? = null
    @Volatile private var connectingSocket: SSLSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        scheduleConnect(0)
    }

    fun send(command: OutgoingCommand, completion: ((Boolean) -> Unit)? = null): Boolean {
        val session = currentSession ?: return false
        val seq = sequence.incrementAndGet()
        val frame = ProtocolCodec.encodeCommand(command, seq, System.currentTimeMillis())
        if (frame.toByteArray(StandardCharsets.UTF_8).size > MAX_LINE_BYTES) return false
        if (completion != null) pendingCompletions[seq] = completion
        val scheduled = runCatching {
            executor.execute {
                if (running.get() && currentSession === session) {
                    runCatching { writeLine(session, frame) }
                        .onFailure {
                            completePending(seq, delivered = false)
                            session.closeQuietly()
                        }
                } else {
                    completePending(seq, delivered = false)
                }
            }
        }.isSuccess
        if (!scheduled) {
            completePending(seq, delivered = false)
            return false
        }
        return true
    }

    override fun close() {
        if (!running.getAndSet(false)) return
        currentSession?.closeQuietly()
        currentSession = null
        connectingSocket?.closeQuietly()
        connectingSocket = null
        failAllPending()
        executor.shutdownNow()
        publishState(State.DISCONNECTED)
    }

    private fun scheduleConnect(delayMillis: Long) {
        if (!running.get() || executor.isShutdown) return
        executor.schedule({ connectAndRead() }, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun connectAndRead() {
        if (!running.get() || !connecting.compareAndSet(false, true)) return
        var session: Session? = null
        try {
            publishState(State.CONNECTING)
            val trustManager = PinningTrustManager(settings.pinnedFingerprint)
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf(trustManager), SecureRandom())
            val socket = context.socketFactory.createSocket() as SSLSocket
            connectingSocket = socket
            socket.useClientMode = true
            socket.tcpNoDelay = true
            socket.soTimeout = READ_TIMEOUT_MILLIS
            socket.connect(InetSocketAddress(settings.host, settings.port), CONNECT_TIMEOUT_MILLIS)
            socket.startHandshake()

            val certificate = socket.session.peerCertificates.first() as X509Certificate
            val actualFingerprint = fingerprintOf(certificate)
            val reader = LimitedLineReader(socket.inputStream)
            val writer = OutputStreamWriter(socket.outputStream, StandardCharsets.UTF_8)

            publishState(State.AUTHENTICATING)
            val challenge = JSONObject(reader.readLine())
            require(challenge.optInt("version") == PROTOCOL_VERSION)
            require(challenge.optString("type") == "challenge")
            val challengeFingerprint = challenge.getString("fingerprint").uppercase(Locale.US)
            require(challengeFingerprint == actualFingerprint) { "证书指纹与握手不一致" }
            val proof = AuthenticationProof.calculate(
                settings.pairingCode,
                challenge.getString("nonce"),
                actualFingerprint,
            )
            writer.write(ProtocolCodec.encodeAuth(proof, "${Build.MANUFACTURER} ${Build.MODEL}"))
            writer.write("\n")
            writer.flush()

            val ready = JSONObject(reader.readLine())
            require(ready.optInt("version") == PROTOCOL_VERSION)
            if (ready.optString("type") != "ready") {
                throw IOException(ready.optString("message", "配对失败"))
            }

            if (settings.pinnedFingerprint.isEmpty()) {
                mainHandler.post { listener.onPinLearned(actualFingerprint) }
            }
            session = Session(socket, writer)
            connectingSocket = null
            currentSession = session
            publishState(State.CONNECTED)

            while (running.get() && currentSession === session) {
                try {
                    val frame = JSONObject(reader.readLine())
                    when (frame.optString("type")) {
                        "ack" -> completePending(frame.optLong("seq", Long.MIN_VALUE), delivered = true)
                        "error" -> throw IOException(frame.optString("message", "接收端拒绝了命令"))
                    }
                } catch (_: SocketTimeoutException) {
                    val pingSeq = sequence.incrementAndGet()
                    writeLine(
                        session,
                        ProtocolCodec.encodeCommand(OutgoingCommand.Ping, pingSeq, System.currentTimeMillis()),
                    )
                }
            }
        } catch (error: Exception) {
            if (running.get()) publishError(error.message ?: "无法连接电脑")
        } finally {
            connecting.set(false)
            connectingSocket = null
            if (currentSession === session) currentSession = null
            failAllPending()
            session?.closeQuietly()
            if (running.get()) {
                publishState(State.DISCONNECTED)
                scheduleConnect(RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun writeLine(session: Session, frame: String) {
        require(frame.toByteArray(StandardCharsets.UTF_8).size <= MAX_LINE_BYTES)
        synchronized(session.writeLock) {
            session.writer.write(frame)
            session.writer.write("\n")
            session.writer.flush()
        }
    }

    private fun publishState(state: State) = mainHandler.post { listener.onStateChanged(state) }

    private fun publishError(message: String) = mainHandler.post { listener.onConnectionError(message) }

    private fun completePending(seq: Long, delivered: Boolean) {
        pendingCompletions.remove(seq)?.let { completion ->
            mainHandler.post { completion(delivered) }
        }
    }

    private fun failAllPending() {
        pendingCompletions.keys.toList().forEach { seq -> completePending(seq, delivered = false) }
    }

    private fun Closeable.closeQuietly() {
        runCatching { close() }
    }

    @SuppressLint("CustomX509TrustManager")
    private class PinningTrustManager(private val expectedFingerprint: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val certificate = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("缺少服务端证书")
            val actual = fingerprintOf(certificate)
            if (!CertificatePinPolicy.accepts(expectedFingerprint, actual)) {
                throw java.security.cert.CertificateException("服务端证书已变化")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private class LimitedLineReader(private val input: InputStream) {
        fun readLine(): String {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                if (next == -1) throw IOException("连接已关闭")
                if (next == '\n'.code) break
                if (next != '\r'.code) {
                    if (buffer.size() >= MAX_LINE_BYTES) throw IOException("服务端消息过大")
                    buffer.write(next)
                }
            }
            return buffer.toString(StandardCharsets.UTF_8.name())
        }
    }

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val MAX_LINE_BYTES = 65_536
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val RECONNECT_DELAY_MILLIS = 2_000L
    }
}
