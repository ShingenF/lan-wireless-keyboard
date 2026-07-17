using System.IO;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Threading;
using Microsoft.Win32;
using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver;

internal sealed class WindowsSystemThemeSource : ISystemThemeSource
{
    private const int WmSettingChange = 0x001A;
    private const int WmThemeChanged = 0x031A;
    private const string PersonalizeKey = @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize";

    private readonly Window _window;
    private HwndSource? _source;
    private volatile bool _disposed;

    public WindowsSystemThemeSource(Window window)
    {
        _window = window;
        _window.SourceInitialized += OnSourceInitialized;
        SystemEvents.UserPreferenceChanged += OnUserPreferenceChanged;
        AttachHook();
    }

    public event EventHandler? ThemeChanged;

    public int? SystemUsesLightTheme
    {
        get
        {
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(PersonalizeKey);
                return key?.GetValue("SystemUsesLightTheme") switch
                {
                    int value => value,
                    long value => checked((int)value),
                    _ => null
                };
            }
            catch (Exception ex) when (ex is UnauthorizedAccessException or IOException or System.Security.SecurityException)
            {
                return null;
            }
        }
    }

    private void OnSourceInitialized(object? sender, EventArgs e) => AttachHook();

    private void AttachHook()
    {
        if (_disposed || _source is not null) return;
        _source = PresentationSource.FromVisual(_window) as HwndSource;
        _source?.AddHook(WindowProc);
    }

    private IntPtr WindowProc(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (message is WmSettingChange or WmThemeChanged) RaiseThemeChanged();
        return IntPtr.Zero;
    }

    private void OnUserPreferenceChanged(object sender, UserPreferenceChangedEventArgs e) => RaiseThemeChanged();

    private void RaiseThemeChanged()
    {
        if (!_disposed) ThemeChanged?.Invoke(this, EventArgs.Empty);
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        SystemEvents.UserPreferenceChanged -= OnUserPreferenceChanged;
        _window.SourceInitialized -= OnSourceInitialized;
        _source?.RemoveHook(WindowProc);
        _source = null;
    }
}

internal sealed class WpfUiDispatcher(Dispatcher dispatcher) : IUiDispatcher
{
    public void Post(Action action)
    {
        if (dispatcher.HasShutdownStarted || dispatcher.HasShutdownFinished) return;
        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(action);
    }
}
