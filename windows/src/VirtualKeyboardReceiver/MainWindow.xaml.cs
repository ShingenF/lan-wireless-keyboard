using System.ComponentModel;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows;
using VirtualKeyboardReceiver.Core;
using Forms = System.Windows.Forms;

namespace VirtualKeyboardReceiver;

public partial class MainWindow : Window, IReceiverApplicationHost
{
    private readonly ReceiverServer _server;
    private readonly ReceiverApplicationLifecycle _lifecycle;
    private readonly Forms.NotifyIcon _trayIcon;
    private readonly Forms.ContextMenuStrip _trayMenu;
    private readonly AdaptiveTrayIconLifetime _trayIconLifetime;
    private bool _trayDisposed;

    public MainWindow(ReceiverIdentity identity, ReceiverServer server)
    {
        InitializeComponent();
        _server = server;
        _lifecycle = new ReceiverApplicationLifecycle(this);
        PairingText.Text = identity.GroupedPairingCode;
        FingerprintText.Text = identity.Fingerprint;
        AddressText.Text = GetLanAddresses();

        _trayMenu = CreateTrayMenu();
        _trayIcon = new Forms.NotifyIcon
        {
            Text = "Virtual Keyboard Receiver — Starting",
            ContextMenuStrip = _trayMenu,
            Visible = false
        };
        _trayIcon.DoubleClick += OnTrayOpen;
        _trayIconLifetime = new AdaptiveTrayIconLifetime(
            new WindowsSystemThemeSource(this),
            new TrayIconLoader(),
            new NotifyIconSurface(_trayIcon),
            new WpfUiDispatcher(Dispatcher));
        _trayIconLifetime.Start();
        _trayIcon.Visible = true;

        server.StatusChanged += status => Dispatcher.InvokeAsync(() =>
        {
            StatusText.Text = status;
            if (!_trayDisposed) _trayIcon.Text = BuildTrayToolTip(status);
        });
        Loaded += async (_, _) =>
        {
            try { await server.StartAsync(); }
            catch (Exception ex)
            {
                StatusText.Text = "Listener error: " + ex.Message;
                _trayIcon.Text = "Virtual Keyboard Receiver — Listener error";
            }
        };
        Closing += OnClosing;
    }

    private Forms.ContextMenuStrip CreateTrayMenu()
    {
        var menu = new Forms.ContextMenuStrip();
        var openItem = new Forms.ToolStripMenuItem("打开");
        var exitItem = new Forms.ToolStripMenuItem("退出");
        openItem.Click += OnTrayOpen;
        exitItem.Click += OnTrayExit;
        menu.Items.Add(openItem);
        menu.Items.Add(exitItem);
        return menu;
    }

    private void OnClosing(object? sender, CancelEventArgs e) => e.Cancel = _lifecycle.HandleWindowClosing();

    private void OnTrayOpen(object? sender, EventArgs e) =>
        Dispatcher.BeginInvoke(_lifecycle.RestoreWindow);

    private void OnTrayExit(object? sender, EventArgs e) =>
        Dispatcher.BeginInvoke(new Action(RequestExplicitExit));

    private async void RequestExplicitExit()
    {
        try { await _lifecycle.ExitAsync(); }
        catch (Exception ex)
        {
            _lifecycle.RestoreWindow();
            StatusText.Text = "Exit error: " + ex.Message;
        }
    }

    void IReceiverApplicationHost.HideWindow() => Hide();

    void IReceiverApplicationHost.ShowWindow()
    {
        Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
    }

    void IReceiverApplicationHost.ActivateWindow() => Activate();

    Task IReceiverApplicationHost.StopReceiverAsync() => _server.StopAsync();

    void IReceiverApplicationHost.DisposeTrayIcon()
    {
        if (_trayDisposed) return;
        _trayDisposed = true;
        _trayIcon.DoubleClick -= OnTrayOpen;
        _trayIconLifetime.Dispose();
        _trayMenu.Dispose();
    }

    void IReceiverApplicationHost.CloseWindow() => Close();

    void IReceiverApplicationHost.ShutdownApplication() => System.Windows.Application.Current.Shutdown();

    private static string BuildTrayToolTip(string status)
    {
        const string prefix = "Virtual Keyboard Receiver — ";
        var available = 63 - prefix.Length;
        return prefix + (status.Length <= available ? status : status[..available]);
    }

    private static string GetLanAddresses()
    {
        var addresses = NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Where(a => a.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a.Address))
            .Select(a => a.Address.ToString()).Distinct().ToArray();
        return addresses.Length == 0 ? "No LAN IPv4 detected" : string.Join(", ", addresses);
    }
}
