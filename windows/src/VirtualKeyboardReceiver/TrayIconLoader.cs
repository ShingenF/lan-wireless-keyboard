using System.Reflection;
using VirtualKeyboardReceiver.Core;
using Forms = System.Windows.Forms;

namespace VirtualKeyboardReceiver;

internal sealed class TrayIconLoader : ITrayIconFactory
{
    private const string BlackResourceName = "VirtualKeyboardReceiver.Assets.tray-hugeicons-keyboard-black.ico";
    private const string WhiteResourceName = "VirtualKeyboardReceiver.Assets.tray-hugeicons-keyboard-white.ico";

    public ITrayIconResource Create(TrayIconTheme theme)
    {
        var resourceName = theme == TrayIconTheme.White ? WhiteResourceName : BlackResourceName;
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(resourceName)
            ?? throw new InvalidOperationException($"Embedded tray icon resource '{resourceName}' was not found.");
        using var source = new System.Drawing.Icon(stream);
        return new DrawingTrayIconResource((System.Drawing.Icon)source.Clone());
    }
}

internal sealed class DrawingTrayIconResource(System.Drawing.Icon icon) : ITrayIconResource
{
    private bool _disposed;
    public System.Drawing.Icon Icon { get; } = icon;

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Icon.Dispose();
    }
}

internal sealed class NotifyIconSurface(Forms.NotifyIcon notifyIcon) : ITrayIconSurface
{
    private bool _disposed;

    public void SetIcon(ITrayIconResource icon)
    {
        ObjectDisposedException.ThrowIf(_disposed, this);
        notifyIcon.Icon = ((DrawingTrayIconResource)icon).Icon;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        notifyIcon.Visible = false;
        notifyIcon.Dispose();
    }
}
