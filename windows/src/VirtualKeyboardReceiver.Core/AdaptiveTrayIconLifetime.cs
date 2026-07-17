namespace VirtualKeyboardReceiver.Core;

public enum TrayIconTheme
{
    Black,
    White
}

public interface ISystemThemeSource : IDisposable
{
    event EventHandler? ThemeChanged;
    int? SystemUsesLightTheme { get; }
}

public interface ITrayIconResource : IDisposable;

public interface ITrayIconFactory
{
    ITrayIconResource Create(TrayIconTheme theme);
}

public interface ITrayIconSurface : IDisposable
{
    void SetIcon(ITrayIconResource icon);
}

public interface IUiDispatcher
{
    void Post(Action action);
}

public sealed class AdaptiveTrayIconLifetime(
    ISystemThemeSource themeSource,
    ITrayIconFactory iconFactory,
    ITrayIconSurface traySurface,
    IUiDispatcher dispatcher) : IDisposable
{
    private readonly object _gate = new();
    private ITrayIconResource? _currentIcon;
    private TrayIconTheme? _currentTheme;
    private bool _started;
    private bool _disposed;

    public void Start()
    {
        lock (_gate)
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (_started) return;
            _started = true;
            themeSource.ThemeChanged += OnThemeChanged;
        }

        dispatcher.Post(ApplyCurrentTheme);
    }

    private void OnThemeChanged(object? sender, EventArgs e) => dispatcher.Post(ApplyCurrentTheme);

    private void ApplyCurrentTheme()
    {
        lock (_gate)
        {
            if (_disposed) return;

            var nextTheme = themeSource.SystemUsesLightTheme == 0
                ? TrayIconTheme.White
                : TrayIconTheme.Black;
            if (_currentTheme == nextTheme) return;

            var nextIcon = iconFactory.Create(nextTheme);
            try
            {
                traySurface.SetIcon(nextIcon);
            }
            catch
            {
                nextIcon.Dispose();
                throw;
            }

            var previousIcon = _currentIcon;
            _currentIcon = nextIcon;
            _currentTheme = nextTheme;
            previousIcon?.Dispose();
        }
    }

    public void Dispose()
    {
        lock (_gate)
        {
            if (_disposed) return;
            _disposed = true;
            if (_started) themeSource.ThemeChanged -= OnThemeChanged;
            themeSource.Dispose();
            traySurface.Dispose();
            _currentIcon?.Dispose();
            _currentIcon = null;
        }
    }
}
