using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace VirtualKeyboardReceiver.Core;

public sealed class ReceiverServer : IAsyncDisposable
{
    private readonly ReceiverIdentity _identity;
    private readonly IInputInjector _injector;
    private readonly int _configuredPort;
    private readonly TimeSpan _authenticationTimeout;
    private readonly SemaphoreSlim _pendingAuthentications;
    private TcpListener? _listener;
    private CancellationTokenSource? _lifetime;
    private Task? _acceptLoop;
    private int _authenticated;
    private readonly HashSet<Task> _sessions = [];
    private readonly object _gate = new();
    public event Action<string>? StatusChanged;
    public event Action<Exception>? CleanupFailed;
    public int Port { get; private set; }
    public bool IsListening => _listener is not null;

    public ReceiverServer(
        ReceiverIdentity identity,
        IInputInjector injector,
        int port = ProtocolConstants.DefaultPort,
        TimeSpan? authenticationTimeout = null,
        int maxPendingAuthentications = 4)
    {
        var resolvedAuthenticationTimeout = authenticationTimeout ?? TimeSpan.FromSeconds(10);
        if (resolvedAuthenticationTimeout <= TimeSpan.Zero) throw new ArgumentOutOfRangeException(nameof(authenticationTimeout));
        if (maxPendingAuthentications <= 0) throw new ArgumentOutOfRangeException(nameof(maxPendingAuthentications));
        _identity = identity;
        _injector = injector;
        _configuredPort = port;
        _authenticationTimeout = resolvedAuthenticationTimeout;
        _pendingAuthentications = new SemaphoreSlim(maxPendingAuthentications, maxPendingAuthentications);
        Port = port;
    }

    public Task StartAsync(CancellationToken cancellationToken = default)
    {
        if (_listener is not null) throw new InvalidOperationException("Server is already running.");
        _lifetime = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _listener = new TcpListener(IPAddress.Any, _configuredPort);
        _listener.Start();
        Port = ((IPEndPoint)_listener.LocalEndpoint).Port;
        _acceptLoop = AcceptLoopAsync(_lifetime.Token);
        StatusChanged?.Invoke("Listening");
        return Task.CompletedTask;
    }

    private async Task AcceptLoopAsync(CancellationToken cancellationToken)
    {
        try
        {
            while (!cancellationToken.IsCancellationRequested)
            {
                var client = await _listener!.AcceptTcpClientAsync(cancellationToken).ConfigureAwait(false);
                var session = HandleClientAsync(client, cancellationToken);
                lock (_gate) _sessions.Add(session);
                _ = session.ContinueWith(t => { lock (_gate) _sessions.Remove(t); }, TaskScheduler.Default);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested) { }
        catch (ObjectDisposedException) when (cancellationToken.IsCancellationRequested) { }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken cancellationToken)
    {
        using var ownedClient = client;
        var dispatcher = new CommandDispatcher(_injector);
        var ownsSlot = false;
        var ownsPendingPermit = false;
        try
        {
            client.NoDelay = true;
            if (!await _pendingAuthentications.WaitAsync(0, cancellationToken).ConfigureAwait(false))
            {
                return;
            }
            ownsPendingPermit = true;

            using (var ssl = new SslStream(client.GetStream(), false))
            {
                using var authenticationDeadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
                authenticationDeadline.CancelAfter(_authenticationTimeout);
                var authenticationToken = authenticationDeadline.Token;
                await ssl.AuthenticateAsServerAsync(new SslServerAuthenticationOptions
                {
                    ServerCertificate = _identity.Certificate,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                    ClientCertificateRequired = false
                }, authenticationToken).ConfigureAwait(false);

                if (Volatile.Read(ref _authenticated) != 0)
                {
                    await ErrorAsync(ssl, "busy", authenticationToken).ConfigureAwait(false);
                    return;
                }

                try
                {
                    var nonce = Convert.ToBase64String(RandomNumberGenerator.GetBytes(32));
                    await JsonLineFraming.WriteAsync(ssl, new { version = 1, type = "challenge", nonce, fingerprint = _identity.Fingerprint }, authenticationToken).ConfigureAwait(false);
                    var authLine = await JsonLineFraming.ReadLineAsync(ssl, authenticationToken).ConfigureAwait(false);
                    if (authLine is null) return;
                    var auth = ProtocolParser.ParseAuth(authLine);
                    if (!Authentication.VerifyProof(_identity.PairingCode, nonce, _identity.Fingerprint, auth.Proof))
                    {
                        await ErrorAsync(ssl, "authentication_failed", authenticationToken).ConfigureAwait(false);
                        return;
                    }
                    if (Interlocked.CompareExchange(ref _authenticated, 1, 0) != 0)
                    {
                        await ErrorAsync(ssl, "busy", authenticationToken).ConfigureAwait(false);
                        return;
                    }
                    ownsSlot = true;
                    authenticationDeadline.CancelAfter(Timeout.InfiniteTimeSpan);
                    _pendingAuthentications.Release();
                    ownsPendingPermit = false;
                    StatusChanged?.Invoke("Connected: " + auth.Device);
                    await JsonLineFraming.WriteAsync(ssl, new { version = 1, type = "ready" }, cancellationToken).ConfigureAwait(false);

                    while (!cancellationToken.IsCancellationRequested)
                    {
                        var line = await JsonLineFraming.ReadLineAsync(ssl, cancellationToken).ConfigureAwait(false);
                        if (line is null) break;
                        var command = ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(line));
                        dispatcher.Dispatch(command, cancellationToken);
                        await JsonLineFraming.WriteAsync(ssl, new { version = 1, type = "ack", seq = command.Seq }, cancellationToken).ConfigureAwait(false);
                        if (command is PingCommand)
                            await JsonLineFraming.WriteAsync(ssl, new { version = 1, type = "pong", seq = command.Seq }, cancellationToken).ConfigureAwait(false);
                    }
                }
                catch (ProtocolException ex)
                {
                    await ErrorAsync(ssl, "protocol_error", cancellationToken, ex.Message).ConfigureAwait(false);
                }
            }
        }
        catch (Exception) when (cancellationToken.IsCancellationRequested) { }
        catch (OperationCanceledException) { }
        catch (IOException) { }
        catch (SocketException) { }
        catch (AuthenticationException) { }
        finally
        {
            try
            {
                dispatcher.ReleaseHeldInputs();
            }
            catch (Exception ex)
            {
                try { CleanupFailed?.Invoke(ex); }
                catch { }
            }
            finally
            {
                if (ownsSlot)
                {
                    Interlocked.Exchange(ref _authenticated, 0);
                    StatusChanged?.Invoke("Listening");
                }
                if (ownsPendingPermit) _pendingAuthentications.Release();
            }
        }
    }

    private static Task ErrorAsync(Stream stream, string code, CancellationToken token, string? detail = null) =>
        JsonLineFraming.WriteAsync(stream, detail is null ? new { version = 1, type = "error", code } : new { version = 1, type = "error", code, detail }, token);

    public async Task StopAsync()
    {
        var listener = Interlocked.Exchange(ref _listener, null);
        if (listener is null) return;
        _lifetime!.Cancel();
        listener.Stop();
        if (_acceptLoop is not null) try { await _acceptLoop.ConfigureAwait(false); } catch { }
        Task[] sessions;
        lock (_gate) sessions = [.. _sessions];
        try { await Task.WhenAll(sessions).ConfigureAwait(false); } catch { }
        _lifetime.Dispose();
        _lifetime = null;
        StatusChanged?.Invoke("Stopped");
    }

    public async ValueTask DisposeAsync() => await StopAsync().ConfigureAwait(false);
}
