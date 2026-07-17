package com.local.virtualkeyboard.protocol

import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object AuthenticationProof {
    fun normalizePairingCode(pairingCode: String): String = pairingCode
        .replace("-", "")
        .replace(" ", "")
        .uppercase(Locale.US)

    fun calculate(pairingCode: String, nonce: String, fingerprint: String): String {
        val normalizedCode = normalizePairingCode(pairingCode)
        val data = "$nonce:${fingerprint.uppercase(Locale.US)}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(normalizedCode.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8)).joinToString("") { byte ->
            "%02X".format(byte.toInt() and 0xff)
        }
    }
}

