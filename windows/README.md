# Virtual Keyboard Receiver

Windows WPF receiver for the v1 virtual-keyboard TLS protocol. It listens on TCP 39421 immediately, displays the LAN address, pairing code, and certificate fingerprint, and injects authenticated keyboard/mouse commands through Win32 `SendInput`.

## Run

The release ZIP contains `VirtualKeyboardReceiver.exe` and requires the .NET 10 Desktop Runtime (x64). Double-click the EXE and approve UAC if Windows prompts: the application manifest requests administrator privileges so the receiver can inject into normal and elevated applications. Windows Firewall may also ask whether to allow private-network access; LAN clients require that permission.

The Windows product version is `1.1.0`; its assembly and file versions are `1.1.0.0`. This packaging version is independent of the wire protocol, which remains protocol v1.

Minimizing keeps the listener active. Clicking the window close button hides the receiver to its Windows notification-area icon and keeps listening. Double-click the icon, or right-click it and choose `打开`, to restore and activate the window. Right-click and choose `退出` to stop the listener, close the client, release held mouse buttons, remove the icon, and terminate the process. There is no service, autostart, hidden background self-start, or silent elevation. Even when elevated, the receiver cannot inject into the UAC secure desktop.

The EXE, WPF window, and notification area use the MIT-licensed Hugeicons `KeyboardIcon` Stroke Rounded. The notification area automatically switches between black on a light Windows system theme and white on a dark theme. The official source data, generated multi-size ICO resources, reproducible generator, and attribution are documented under `src/VirtualKeyboardReceiver/Assets` and `THIRD_PARTY_NOTICES.md`.

First launch creates persistent credentials under gitignored `runtime-data/`. When the executable runs from this project, it continues to use the project-root directory so existing credentials remain stable. When copied to an ordinary standalone directory without the project marker, it uses `runtime-data/` beside the executable. The Android client must compare the challenge fingerprint with the actual TLS certificate fingerprint before computing its authentication proof. See [docs/SPEC.md](docs/SPEC.md) for the exact protocol.

## Build and test

```powershell
dotnet test VirtualKeyboardReceiver.slnx
dotnet publish src\VirtualKeyboardReceiver\VirtualKeyboardReceiver.csproj -c Release -r win-x64 --self-contained false -p:PublishSingleFile=true -o artifacts\windows-x64
```
