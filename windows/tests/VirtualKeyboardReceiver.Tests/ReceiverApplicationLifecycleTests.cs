using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class ReceiverApplicationLifecycleTests
{
    [Fact]
    public void WindowClose_HidesWindowWithoutStoppingReceiverOrExiting()
    {
        var host = new RecordingApplicationHost();
        var lifecycle = new ReceiverApplicationLifecycle(host);

        var cancelClose = lifecycle.HandleWindowClosing();

        Assert.True(cancelClose);
        Assert.Equal(["hide"], host.Events);
        Assert.False(lifecycle.IsExitRequested);
    }

    [Fact]
    public void TrayRestore_ShowsAndActivatesWindow()
    {
        var host = new RecordingApplicationHost();
        var lifecycle = new ReceiverApplicationLifecycle(host);

        lifecycle.RestoreWindow();

        Assert.Equal(["show", "activate"], host.Events);
    }

    [Fact]
    public async Task ExplicitExit_StopsReceiverDisposesTrayClosesAndShutsDown_Once()
    {
        var host = new RecordingApplicationHost();
        var lifecycle = new ReceiverApplicationLifecycle(host);

        await Task.WhenAll(lifecycle.ExitAsync(), lifecycle.ExitAsync());

        Assert.True(lifecycle.IsExitRequested);
        Assert.False(lifecycle.HandleWindowClosing());
        Assert.Equal(["stop", "dispose-tray", "close", "shutdown"], host.Events);
    }

    [Fact]
    public async Task ExplicitExit_StillDisposesAndTerminatesWhenReceiverStopFails()
    {
        var host = new RecordingApplicationHost { ThrowWhenStopping = true };
        var lifecycle = new ReceiverApplicationLifecycle(host);

        await Assert.ThrowsAsync<InvalidOperationException>(() => lifecycle.ExitAsync());

        Assert.True(lifecycle.IsExitRequested);
        Assert.Equal(["stop", "dispose-tray", "close", "shutdown"], host.Events);
    }

    [Theory]
    [InlineData("dispose-tray")]
    [InlineData("close")]
    public async Task ExplicitExit_CleanupFailureStillAttemptsEveryLaterStepAndShutdown(string failingStep)
    {
        var host = new RecordingApplicationHost { FailingStep = failingStep };
        var lifecycle = new ReceiverApplicationLifecycle(host);

        var error = await Assert.ThrowsAsync<InvalidOperationException>(() => lifecycle.ExitAsync());

        Assert.Equal(failingStep + " failed", error.Message);
        Assert.True(lifecycle.IsExitRequested);
        Assert.Equal(["stop", "dispose-tray", "close", "shutdown"], host.Events);
        await Assert.ThrowsAsync<InvalidOperationException>(() => lifecycle.ExitAsync());
        Assert.Equal(["stop", "dispose-tray", "close", "shutdown"], host.Events);
    }

    private sealed class RecordingApplicationHost : IReceiverApplicationHost
    {
        public List<string> Events { get; } = [];
        public bool ThrowWhenStopping { get; init; }
        public string? FailingStep { get; init; }
        public void HideWindow() => Events.Add("hide");
        public void ShowWindow() => Events.Add("show");
        public void ActivateWindow() => Events.Add("activate");
        public Task StopReceiverAsync()
        {
            Events.Add("stop");
            return ThrowWhenStopping ? Task.FromException(new InvalidOperationException("stop failed")) : Task.CompletedTask;
        }
        public void DisposeTrayIcon() => Record("dispose-tray");
        public void CloseWindow() => Record("close");
        public void ShutdownApplication() => Record("shutdown");
        private void Record(string step)
        {
            Events.Add(step);
            if (FailingStep == step) throw new InvalidOperationException(step + " failed");
        }
    }
}
