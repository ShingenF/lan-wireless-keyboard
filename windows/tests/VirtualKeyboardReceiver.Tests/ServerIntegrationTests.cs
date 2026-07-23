using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class ServerIntegrationTests
{
    [Fact]
    public async Task ExplicitExitDuringReplaceTail_CancelsDispatchAndCompletesPromptly()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        var injector = new SlowBackspaceInjector();
        await using var server = new ReceiverServer(identity, injector, 0);
        await server.StartAsync();
        await using var client = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
        await client.WriteAsync($"{{\"version\":1,\"type\":\"replaceTail\",\"seq\":84,\"timestamp\":94,\"deleteCodePoints\":{ProtocolConstants.MaxReplaceTailCodePoints},\"text\":\"replacement\"}}");
        Assert.True(injector.FirstBackspace.Wait(TimeSpan.FromSeconds(2)));
        var host = new ServerApplicationHost(server);
        var lifecycle = new ReceiverApplicationLifecycle(host);

        await lifecycle.ExitAsync().WaitAsync(TimeSpan.FromSeconds(2));

        Assert.True(host.ShutdownCalled);
        Assert.False(server.IsListening);
        Assert.InRange(injector.BackspaceCount, 1, ProtocolConstants.MaxReplaceTailCodePoints - 1);
        Assert.False(injector.TextCommitted);
    }

    [Fact]
    public async Task StopDuringPendingPermitAndAcceptBurst_ClosesSocketsAndRestartRemainsUsable()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromSeconds(30),
            maxPendingAuthentications: 1);
        await server.StartAsync();
        await using var pending = await TestClient.ConnectTlsOnlyAsync(server.Port);
        Assert.Equal("challenge", JsonDocument.Parse(await pending.ReadAsync()).RootElement.GetProperty("type").GetString());
        var firstConnected = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var connectTasks = Enumerable.Range(0, 64).Select(async _ =>
        {
            var tcp = new TcpClient();
            try
            {
                await tcp.ConnectAsync("127.0.0.1", server.Port).WaitAsync(TimeSpan.FromSeconds(2));
                firstConnected.TrySetResult();
                return tcp;
            }
            catch (Exception ex) when (ex is SocketException or TimeoutException)
            {
                tcp.Dispose();
                return null;
            }
        }).ToArray();
        await firstConnected.Task.WaitAsync(TimeSpan.FromSeconds(2));

        var stop = server.StopAsync();
        var burst = (await Task.WhenAll(connectTasks)).Where(client => client is not null).Cast<TcpClient>().ToArray();
        await stop.WaitAsync(TimeSpan.FromSeconds(2));

        foreach (var tcp in burst)
        {
            await AssertConnectionClosedAsync(tcp, TimeSpan.FromSeconds(2));
            tcp.Dispose();
        }
        await server.StartAsync();
        await using var recovered = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await recovered.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task AuthenticationDeadline_ClosesStalledHandshakeAndReleasesPendingPermit()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromMilliseconds(250),
            maxPendingAuthentications: 1);
        await server.StartAsync();

        using (var stalled = new TcpClient())
        {
            await stalled.ConnectAsync("127.0.0.1", server.Port);
            await AssertConnectionClosedAsync(stalled, TimeSpan.FromSeconds(2));
        }

        await using var recovered = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await recovered.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task PendingAuthenticationLimit_RejectsExcessAndPermitIsReleasedInFinally()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromSeconds(5),
            maxPendingAuthentications: 1);
        await server.StartAsync();

        await using (var pending = await TestClient.ConnectTlsOnlyAsync(server.Port))
        {
            Assert.Equal("challenge", JsonDocument.Parse(await pending.ReadAsync()).RootElement.GetProperty("type").GetString());
            using var excess = new TcpClient();
            await excess.ConnectAsync("127.0.0.1", server.Port);
            await AssertConnectionClosedAsync(excess, TimeSpan.FromSeconds(2));
        }

        await using var recovered = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await recovered.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task ExcessPendingConnection_DoesNotAffectAuthenticatedClient()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromSeconds(5),
            maxPendingAuthentications: 1);
        await server.StartAsync();
        await using var authenticated = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await authenticated.ReadAsync()).RootElement.GetProperty("type").GetString());

        using var stalled = new TcpClient();
        await stalled.ConnectAsync("127.0.0.1", server.Port);
        await Task.Delay(100);
        using var excess = new TcpClient();
        await excess.ConnectAsync("127.0.0.1", server.Port);
        await AssertConnectionClosedAsync(excess, TimeSpan.FromSeconds(2));

        await authenticated.WriteAsync("{\"version\":1,\"type\":\"ping\",\"seq\":83,\"timestamp\":93}");
        Assert.Equal("ack", JsonDocument.Parse(await authenticated.ReadAsync()).RootElement.GetProperty("type").GetString());
        Assert.Equal("pong", JsonDocument.Parse(await authenticated.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task StopWithPendingAuthentication_ReleasesPermitAndLeavesRestartUsable()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromSeconds(30),
            maxPendingAuthentications: 1);
        await server.StartAsync();

        await using (var pending = await TestClient.ConnectTlsOnlyAsync(server.Port))
        {
            Assert.Equal("challenge", JsonDocument.Parse(await pending.ReadAsync()).RootElement.GetProperty("type").GetString());
            await server.StopAsync().WaitAsync(TimeSpan.FromSeconds(2));
            Assert.False(server.IsListening);
        }

        await server.StartAsync();
        await using var recovered = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await recovered.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task AuthenticationDeadline_DoesNotApplyToAuthenticatedCommandStream()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(
            identity,
            new ThreadSafeInjector(),
            0,
            authenticationTimeout: TimeSpan.FromSeconds(1));
        await server.StartAsync();
        await using var client = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());

        await Task.Delay(TimeSpan.FromMilliseconds(1200));
        await client.WriteAsync("{\"version\":1,\"type\":\"ping\",\"seq\":81,\"timestamp\":91}");

        Assert.Equal("ack", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
        Assert.Equal("pong", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task ReleaseHeldInputsFailure_StillRestoresAuthenticationSlotForNextClient()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        var listeningAgain = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var connectedSeen = 0;
        Exception? cleanupFailure = null;
        await using var server = new ReceiverServer(identity, new ThrowOnReleaseInjector(), 0);
        server.CleanupFailed += exception => Interlocked.Exchange(ref cleanupFailure, exception);
        server.StatusChanged += status =>
        {
            if (status.StartsWith("Connected:", StringComparison.Ordinal)) Interlocked.Exchange(ref connectedSeen, 1);
            if (status == "Listening" && Volatile.Read(ref connectedSeen) != 0) listeningAgain.TrySetResult();
        };
        await server.StartAsync();

        await using (var first = await TestClient.ConnectAsync(server.Port, identity.PairingCode))
        {
            Assert.Equal("ready", JsonDocument.Parse(await first.ReadAsync()).RootElement.GetProperty("type").GetString());
            await first.WriteAsync("{\"version\":1,\"type\":\"pointerButton\",\"seq\":82,\"timestamp\":92,\"button\":\"left\",\"action\":\"down\"}");
            _ = await first.ReadAsync();
        }

        await listeningAgain.Task.WaitAsync(TimeSpan.FromSeconds(2));
        Assert.Equal("release failed", cleanupFailure?.Message);
        await using var second = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        Assert.Equal("ready", JsonDocument.Parse(await second.ReadAsync()).RootElement.GetProperty("type").GetString());
    }

    [Fact]
    public async Task TlsAuthentication_CommandAckPong_DisconnectRelease_AndShutdownStopsListener()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        var fake = new ThreadSafeInjector();
        await using var server = new ReceiverServer(identity, fake, 0);
        await server.StartAsync();

        await using (var client = await TestClient.ConnectAsync(server.Port, identity.PairingCode))
        {
            Assert.Equal(identity.Fingerprint, client.ObservedFingerprint);
            Assert.Equal("ready", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
            await client.WriteAsync("{\"version\":1,\"type\":\"pointerButton\",\"seq\":10,\"timestamp\":20,\"button\":\"left\",\"action\":\"down\"}");
            Assert.Equal(10, JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("seq").GetInt64());
            await client.WriteAsync("{\"version\":1,\"type\":\"keyState\",\"seq\":13,\"timestamp\":23,\"key\":\"w\",\"action\":\"down\"}");
            Assert.Equal(13, JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("seq").GetInt64());
            await client.WriteAsync("{\"version\":1,\"type\":\"ping\",\"seq\":11,\"timestamp\":21}");
            Assert.Equal("ack", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
            Assert.Equal("pong", JsonDocument.Parse(await client.ReadAsync()).RootElement.GetProperty("type").GetString());
        }

        await WaitUntilAsync(() => fake.Events.Contains("button:Left,Up"));
        await WaitUntilAsync(() => fake.Events.Contains("game:W,Up"));
        await server.StopAsync();
        Assert.False(server.IsListening);
        await Assert.ThrowsAnyAsync<SocketException>(() => new TcpClient().ConnectAsync("127.0.0.1", server.Port));
    }

    [Fact]
    public async Task WrongPairingCodeGetsError_AndAuthenticatedClientMakesSecondClientBusy()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        await using var server = new ReceiverServer(identity, new ThreadSafeInjector(), 0);
        await server.StartAsync();

        await using (var wrong = await TestClient.ConnectAsync(server.Port, "AAAAAAAAAAAAAAAA", expectReady: false))
            Assert.Equal("authentication_failed", JsonDocument.Parse(await wrong.ReadAsync()).RootElement.GetProperty("code").GetString());

        await using var first = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        _ = await first.ReadAsync();
        await using var second = await TestClient.ConnectTlsOnlyAsync(server.Port);
        var busy = JsonDocument.Parse(await second.ReadAsync()).RootElement;
        Assert.Equal("busy", busy.GetProperty("code").GetString());
    }

    [Fact]
    public async Task ShutdownWithHeldMouseButton_ReleasesButtonAndStopsListener()
    {
        using var temp = new TempDirectory();
        var identity = ReceiverIdentity.LoadOrCreate(temp.Path);
        var fake = new ThreadSafeInjector();
        await using var server = new ReceiverServer(identity, fake, 0);
        await server.StartAsync();
        await using var client = await TestClient.ConnectAsync(server.Port, identity.PairingCode);
        _ = await client.ReadAsync();
        await client.WriteAsync("{\"version\":1,\"type\":\"pointerButton\",\"seq\":12,\"timestamp\":22,\"button\":\"right\",\"action\":\"down\"}");
        _ = await client.ReadAsync();
        await client.WriteAsync("{\"version\":1,\"type\":\"keyState\",\"seq\":14,\"timestamp\":24,\"key\":\"w\",\"action\":\"down\"}");
        _ = await client.ReadAsync();

        await server.StopAsync();

        Assert.Contains("button:Right,Up", fake.Events);
        Assert.Contains("game:W,Up", fake.Events);
        Assert.False(server.IsListening);
    }

    private static async Task WaitUntilAsync(Func<bool> condition)
    {
        for (var i = 0; i < 100 && !condition(); i++) await Task.Delay(10);
        Assert.True(condition());
    }

    private static async Task AssertConnectionClosedAsync(TcpClient client, TimeSpan timeout)
    {
        try
        {
            var buffer = new byte[1];
            var count = await client.GetStream().ReadAsync(buffer).AsTask().WaitAsync(timeout);
            Assert.Equal(0, count);
        }
        catch (IOException)
        {
        }
        catch (SocketException)
        {
        }
    }

    private sealed class TestClient : IAsyncDisposable
    {
        private readonly TcpClient _tcp;
        private readonly SslStream _ssl;
        public string ObservedFingerprint { get; private set; } = "";
        private TestClient(TcpClient tcp, SslStream ssl) { _tcp = tcp; _ssl = ssl; }

        public static async Task<TestClient> ConnectTlsOnlyAsync(int port)
        {
            var tcp = new TcpClient { NoDelay = true };
            await tcp.ConnectAsync("127.0.0.1", port);
            var result = new TestClient(tcp, new SslStream(tcp.GetStream(), false, (sender, cert, chain, errors) => true));
            await result._ssl.AuthenticateAsClientAsync("Virtual Keyboard Receiver");
            result.ObservedFingerprint = Convert.ToHexString(SHA256.HashData(new X509Certificate2(result._ssl.RemoteCertificate!).Export(X509ContentType.Cert)));
            return result;
        }

        public static async Task<TestClient> ConnectAsync(int port, string code, bool expectReady = true)
        {
            var result = await ConnectTlsOnlyAsync(port);
            var challenge = JsonDocument.Parse(await result.ReadAsync()).RootElement;
            var nonce = challenge.GetProperty("nonce").GetString()!;
            var fingerprint = challenge.GetProperty("fingerprint").GetString()!;
            Assert.Equal(result.ObservedFingerprint, fingerprint);
            var proof = Authentication.ComputeProof(code, nonce, fingerprint);
            await result.WriteAsync(JsonSerializer.Serialize(new { version = 1, type = "auth", proof, device = "test" }));
            return result;
        }

        public Task WriteAsync(string json) => JsonLineFraming.WriteAsync(_ssl, JsonDocument.Parse(json).RootElement.Clone(), default);
        public async Task<string> ReadAsync() => await JsonLineFraming.ReadLineAsync(_ssl, default) ?? throw new EndOfStreamException();
        public async ValueTask DisposeAsync() { await _ssl.DisposeAsync(); _tcp.Dispose(); }
    }

    private sealed class ThreadSafeInjector : IInputInjector
    {
        public System.Collections.Concurrent.ConcurrentBag<string> Events { get; } = [];
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) => Events.Add("text:" + text);
        public void PressKey(ReceiverKey key) => Events.Add("key:" + key);
        public void MovePointer(int dx, int dy) => Events.Add($"move:{dx},{dy}");
        public void SetPointerButton(MouseButton button, ButtonAction action) => Events.Add($"button:{button},{action}");
        public void SetKeyState(GameKey key, ButtonAction action) => Events.Add($"game:{key},{action}");
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void PressShortcutChord(IReadOnlyList<ShortcutModifier> modifiers, ShortcutKey key) { }
        public void Wheel(int delta) => Events.Add("wheel:" + delta);
    }

    private sealed class ThrowOnReleaseInjector : IInputInjector
    {
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) { }
        public void PressKey(ReceiverKey key) { }
        public void MovePointer(int dx, int dy) { }
        public void SetPointerButton(MouseButton button, ButtonAction action)
        {
            if (action == ButtonAction.Up) throw new InvalidOperationException("release failed");
        }
        public void SetKeyState(GameKey key, ButtonAction action) { }
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void PressShortcutChord(IReadOnlyList<ShortcutModifier> modifiers, ShortcutKey key) { }
        public void Wheel(int delta) { }
    }

    private sealed class SlowBackspaceInjector : IInputInjector
    {
        public ManualResetEventSlim FirstBackspace { get; } = new();
        public int BackspaceCount;
        public bool TextCommitted;
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) => TextCommitted = true;
        public void PressKey(ReceiverKey key)
        {
            if (key != ReceiverKey.Backspace) return;
            Interlocked.Increment(ref BackspaceCount);
            FirstBackspace.Set();
            Thread.Sleep(10);
        }
        public void MovePointer(int dx, int dy) { }
        public void SetPointerButton(MouseButton button, ButtonAction action) { }
        public void SetKeyState(GameKey key, ButtonAction action) { }
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void PressShortcutChord(IReadOnlyList<ShortcutModifier> modifiers, ShortcutKey key) { }
        public void Wheel(int delta) { }
    }

    private sealed class ServerApplicationHost(ReceiverServer server) : IReceiverApplicationHost
    {
        public bool ShutdownCalled { get; private set; }
        public void HideWindow() { }
        public void ShowWindow() { }
        public void ActivateWindow() { }
        public Task StopReceiverAsync() => server.StopAsync();
        public void DisposeTrayIcon() { }
        public void CloseWindow() { }
        public void ShutdownApplication() => ShutdownCalled = true;
    }

    private sealed class TempDirectory : IDisposable
    {
        public string Path { get; } = System.IO.Path.Combine(AppContext.BaseDirectory, "runtime-data-test-" + Guid.NewGuid().ToString("N"));
        public void Dispose() { if (Directory.Exists(Path)) Directory.Delete(Path, true); }
    }
}
