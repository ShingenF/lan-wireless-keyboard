using System.Windows;
using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var runtimeData = RuntimeDataPathResolver.Resolve(AppContext.BaseDirectory);
        var identity = ReceiverIdentity.LoadOrCreate(runtimeData);
        var server = new ReceiverServer(identity, new Win32InputInjector());
        var window = new MainWindow(identity, server);
        MainWindow = window;
        window.Show();
    }
}
