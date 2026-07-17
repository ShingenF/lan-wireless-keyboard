namespace VirtualKeyboardReceiver.Core;

public interface IReceiverApplicationHost
{
    void HideWindow();
    void ShowWindow();
    void ActivateWindow();
    Task StopReceiverAsync();
    void DisposeTrayIcon();
    void CloseWindow();
    void ShutdownApplication();
}

public sealed class ReceiverApplicationLifecycle(IReceiverApplicationHost host)
{
    private readonly object _gate = new();
    private Task? _exitTask;

    public bool IsExitRequested { get; private set; }

    public bool HandleWindowClosing()
    {
        if (IsExitRequested) return false;
        host.HideWindow();
        return true;
    }

    public void RestoreWindow()
    {
        if (IsExitRequested) return;
        host.ShowWindow();
        host.ActivateWindow();
    }

    public Task ExitAsync()
    {
        lock (_gate)
        {
            IsExitRequested = true;
            return _exitTask ??= ExitCoreAsync();
        }
    }

    private async Task ExitCoreAsync()
    {
        System.Runtime.ExceptionServices.ExceptionDispatchInfo? failure = null;
        try
        {
            await host.StopReceiverAsync().ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            failure = System.Runtime.ExceptionServices.ExceptionDispatchInfo.Capture(ex);
        }

        TryCleanup(host.DisposeTrayIcon);
        TryCleanup(host.CloseWindow);
        TryCleanup(host.ShutdownApplication);
        failure?.Throw();

        void TryCleanup(Action cleanup)
        {
            try
            {
                cleanup();
            }
            catch (Exception ex)
            {
                failure ??= System.Runtime.ExceptionServices.ExceptionDispatchInfo.Capture(ex);
            }
        }
    }
}
