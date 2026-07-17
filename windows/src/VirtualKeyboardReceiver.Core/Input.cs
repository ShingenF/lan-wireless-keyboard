using System.ComponentModel;
using System.Runtime.ExceptionServices;
using System.Runtime.InteropServices;

namespace VirtualKeyboardReceiver.Core;

public interface IInputInjector
{
    void InjectText(string text, bool preferPhysicalKeys = false, CancellationToken cancellationToken = default);
    void PressKey(ReceiverKey key);
    void SetKeyState(GameKey key, ButtonAction action);
    void PressSystemShortcut(SystemShortcut shortcut);
    void MovePointer(int dx, int dy);
    void SetPointerButton(MouseButton button, ButtonAction action);
    void Wheel(int delta);
}

public sealed class CommandDispatcher(IInputInjector injector)
{
    private readonly HashSet<MouseButton> _heldButtons = [];
    private readonly HashSet<GameKey> _heldGameKeys = [];
    public void Dispatch(ReceiverCommand command, CancellationToken cancellationToken = default)
    {
        switch (command)
        {
            case TextCommitCommand c: injector.InjectText(c.Text, c.PreferPhysicalKeys, cancellationToken); break;
            case ReplaceTailCommand c:
                for (var i = 0; i < c.DeleteCodePoints; i++)
                {
                    cancellationToken.ThrowIfCancellationRequested();
                    injector.PressKey(ReceiverKey.Backspace);
                }
                cancellationToken.ThrowIfCancellationRequested();
                injector.InjectText(c.Text, c.PreferPhysicalKeys, cancellationToken); break;
            case KeyPressCommand c: injector.PressKey(c.Key); break;
            case KeyStateCommand c: DispatchKeyState(c); break;
            case SystemShortcutCommand c: injector.PressSystemShortcut(c.Shortcut); break;
            case PointerMoveCommand c: injector.MovePointer(c.Dx, c.Dy); break;
            case PointerButtonCommand c:
                injector.SetPointerButton(c.Button, c.Action);
                if (c.Action == ButtonAction.Down) _heldButtons.Add(c.Button); else _heldButtons.Remove(c.Button);
                break;
            case WheelCommand c: injector.Wheel(c.Delta); break;
            case PingCommand: break;
            default: throw new ArgumentOutOfRangeException(nameof(command));
        }
    }
    private void DispatchKeyState(KeyStateCommand command)
    {
        if (command.Action == ButtonAction.Down)
        {
            if (!_heldGameKeys.Add(command.Key)) return;
            try { injector.SetKeyState(command.Key, ButtonAction.Down); }
            catch { _heldGameKeys.Remove(command.Key); throw; }
            return;
        }

        if (!_heldGameKeys.Contains(command.Key)) return;
        injector.SetKeyState(command.Key, ButtonAction.Up);
        _heldGameKeys.Remove(command.Key);
    }
    public void ReleaseHeldInputs()
    {
        Exception? firstFailure = null;
        foreach (var button in _heldButtons.ToArray())
        {
            try { injector.SetPointerButton(button, ButtonAction.Up); }
            catch (Exception ex) { firstFailure ??= ex; }
        }
        foreach (var key in _heldGameKeys.ToArray())
        {
            try { injector.SetKeyState(key, ButtonAction.Up); }
            catch (Exception ex) { firstFailure ??= ex; }
        }
        _heldButtons.Clear();
        _heldGameKeys.Clear();
        if (firstFailure is not null) ExceptionDispatchInfo.Capture(firstFailure).Throw();
    }
}

public interface IWin32SendInput { void Send(INPUT[] inputs); }

[Flags]
public enum PhysicalKeyModifiers { None = 0, Shift = 1, Control = 2, Alt = 4 }
public readonly record struct PhysicalKeyStroke(ushort VirtualKey, ushort ScanCode, PhysicalKeyModifiers Modifiers, bool Extended);
public interface IPhysicalKeyMapper { bool TryMap(char character, out PhysicalKeyStroke stroke); }

public sealed class WindowsPhysicalKeyMapper : IPhysicalKeyMapper
{
    public bool TryMap(char character, out PhysicalKeyStroke stroke)
    {
        stroke = default;
        if (character > 0x7F) return false;
        var window = GetForegroundWindow();
        var thread = window == IntPtr.Zero ? 0u : GetWindowThreadProcessId(window, out _);
        var layout = thread == 0 ? IntPtr.Zero : GetKeyboardLayout(thread);
        if (layout == IntPtr.Zero) return false;
        var mapped = VkKeyScanExW(character, layout);
        if (mapped == -1) return false;
        var modifiers = (PhysicalKeyModifiers)((mapped >> 8) & 0x07);
        if (((mapped >> 8) & ~0x07) != 0) return false;
        var virtualKey = (ushort)(mapped & 0xFF);
        var scan = MapVirtualKeyExW(virtualKey, 4, layout);
        if (scan == 0) return false;
        stroke = new PhysicalKeyStroke(virtualKey, (ushort)(scan & 0xFF), modifiers, (scan & 0xFF00) == 0xE000);
        return true;
    }

    [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);
    [DllImport("user32.dll")] private static extern IntPtr GetKeyboardLayout(uint threadId);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern short VkKeyScanExW(char character, IntPtr keyboardLayout);
    [DllImport("user32.dll")] private static extern uint MapVirtualKeyExW(uint code, uint mapType, IntPtr keyboardLayout);
}

public interface ITextInputPacer { void Pace(); }

public interface IThreadSleeper { void Sleep(int milliseconds); }

public sealed class SystemThreadSleeper : IThreadSleeper
{
    public static SystemThreadSleeper Instance { get; } = new();
    private SystemThreadSleeper() { }
    public void Sleep(int milliseconds) => Thread.Sleep(milliseconds);
}

public sealed class OneMillisecondTextInputPacer(IThreadSleeper? threadSleeper = null) : ITextInputPacer
{
    public static OneMillisecondTextInputPacer Instance { get; } = new();
    private readonly IThreadSleeper _threadSleeper = threadSleeper ?? SystemThreadSleeper.Instance;
    public void Pace() => _threadSleeper.Sleep(1);
}

public sealed class NativeWin32SendInput : IWin32SendInput
{
    public void Send(INPUT[] inputs)
    {
        if (SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>()) != inputs.Length)
            throw new Win32Exception(Marshal.GetLastWin32Error(), "SendInput failed.");
    }
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint count, INPUT[] inputs, int size);
}

public sealed class Win32InputInjector(IWin32SendInput? sender = null, ITextInputPacer? textInputPacer = null, IPhysicalKeyMapper? physicalKeyMapper = null) : IInputInjector
{
    private readonly IWin32SendInput _sender = sender ?? new NativeWin32SendInput();
    private readonly ITextInputPacer _textInputPacer = textInputPacer ?? OneMillisecondTextInputPacer.Instance;
    private readonly IPhysicalKeyMapper _physicalKeyMapper = physicalKeyMapper ?? new WindowsPhysicalKeyMapper();
    public void InjectText(string text, bool preferPhysicalKeys = false, CancellationToken cancellationToken = default)
    {
        for (var index = 0; index < text.Length; index++)
        {
            cancellationToken.ThrowIfCancellationRequested();
            var codeUnit = text[index];
            if (codeUnit is '\r' or '\n')
            {
                if (codeUnit == '\r' && index + 1 < text.Length && text[index + 1] == '\n') index++;
                _sender.Send([INPUT.Keyboard(0x0D, 0, 0), INPUT.Keyboard(0x0D, 0, 0x0002)]);
                _textInputPacer.Pace();
                continue;
            }
            if (preferPhysicalKeys && _physicalKeyMapper.TryMap(codeUnit, out var stroke))
            {
                SendPhysicalKey(stroke);
                _textInputPacer.Pace();
                continue;
            }
            _sender.Send([INPUT.Keyboard(0, codeUnit, 0x0004), INPUT.Keyboard(0, codeUnit, 0x0004 | 0x0002)]);
            _textInputPacer.Pace();
        }
    }
    private void SendPhysicalKey(PhysicalKeyStroke stroke)
    {
        var inputs = new List<INPUT>();
        var modifiers = new List<(ushort ScanCode, bool Extended)>();
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Shift)) modifiers.Add((0x2A, false));
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Control)) modifiers.Add((0x1D, false));
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Alt)) modifiers.Add((0x38, stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Control)));
        foreach (var modifier in modifiers) inputs.Add(INPUT.Keyboard(0, modifier.ScanCode, 0x0008 | (modifier.Extended ? 0x0001u : 0u)));
        var flags = 0x0008u | (stroke.Extended ? 0x0001u : 0u);
        inputs.Add(INPUT.Keyboard(0, stroke.ScanCode, flags));
        inputs.Add(INPUT.Keyboard(0, stroke.ScanCode, flags | 0x0002));
        for (var i = modifiers.Count - 1; i >= 0; i--)
        {
            var modifier = modifiers[i];
            inputs.Add(INPUT.Keyboard(0, modifier.ScanCode, 0x0008 | (modifier.Extended ? 0x0001u : 0u) | 0x0002));
        }
        var compensation = new List<INPUT> { INPUT.Keyboard(0, stroke.ScanCode, flags | 0x0002) };
        for (var i = modifiers.Count - 1; i >= 0; i--)
        {
            var modifier = modifiers[i];
            compensation.Add(INPUT.Keyboard(0, modifier.ScanCode, 0x0008 | (modifier.Extended ? 0x0001u : 0u) | 0x0002));
        }
        SendInputSequence(inputs.ToArray(), compensation.ToArray());
    }
    public void PressKey(ReceiverKey key)
    {
        var vk = key switch { ReceiverKey.Up => 0x26, ReceiverKey.Down => 0x28, ReceiverKey.Left => 0x25, ReceiverKey.Right => 0x27, ReceiverKey.Backspace => 0x08, ReceiverKey.Enter => 0x0D, ReceiverKey.Escape => 0x1B, _ => throw new ArgumentOutOfRangeException(nameof(key)) };
        _sender.Send([INPUT.Keyboard((ushort)vk, 0, 0), INPUT.Keyboard((ushort)vk, 0, 0x0002)]);
    }
    public void SetKeyState(GameKey key, ButtonAction action)
    {
        var scan = key switch { GameKey.W => 0x11, GameKey.A => 0x1E, GameKey.S => 0x1F, GameKey.D => 0x20, _ => throw new ArgumentOutOfRangeException(nameof(key)) };
        var flags = 0x0008u | (action == ButtonAction.Up ? 0x0002u : 0u);
        _sender.Send([INPUT.Keyboard(0, (ushort)scan, flags)]);
    }
    public void PressSystemShortcut(SystemShortcut shortcut)
    {
        const uint scan = 0x0008;
        const uint extendedScan = 0x0009;
        const uint keyUp = 0x0002;
        (INPUT[] Inputs, INPUT[] Compensation) sequence = shortcut switch
        {
            SystemShortcut.Shift => ([INPUT.Keyboard(0, 0x2A, scan), INPUT.Keyboard(0, 0x2A, scan | keyUp)], [INPUT.Keyboard(0, 0x2A, scan | keyUp)]),
            SystemShortcut.ControlSpace => ([INPUT.Keyboard(0, 0x1D, scan), INPUT.Keyboard(0, 0x39, scan), INPUT.Keyboard(0, 0x39, scan | keyUp), INPUT.Keyboard(0, 0x1D, scan | keyUp)], [INPUT.Keyboard(0, 0x39, scan | keyUp), INPUT.Keyboard(0, 0x1D, scan | keyUp)]),
            SystemShortcut.CapsLock => ([INPUT.Keyboard(0, 0x3A, scan), INPUT.Keyboard(0, 0x3A, scan | keyUp)], [INPUT.Keyboard(0, 0x3A, scan | keyUp)]),
            SystemShortcut.WindowsSpace => ([INPUT.Keyboard(0, 0x5B, extendedScan), INPUT.Keyboard(0, 0x39, scan), INPUT.Keyboard(0, 0x39, scan | keyUp), INPUT.Keyboard(0, 0x5B, extendedScan | keyUp)], [INPUT.Keyboard(0, 0x39, scan | keyUp), INPUT.Keyboard(0, 0x5B, extendedScan | keyUp)]),
            SystemShortcut.ControlShift => ([INPUT.Keyboard(0, 0x1D, scan), INPUT.Keyboard(0, 0x2A, scan), INPUT.Keyboard(0, 0x2A, scan | keyUp), INPUT.Keyboard(0, 0x1D, scan | keyUp)], [INPUT.Keyboard(0, 0x2A, scan | keyUp), INPUT.Keyboard(0, 0x1D, scan | keyUp)]),
            SystemShortcut.AltShift => ([INPUT.Keyboard(0, 0x38, scan), INPUT.Keyboard(0, 0x2A, scan), INPUT.Keyboard(0, 0x2A, scan | keyUp), INPUT.Keyboard(0, 0x38, scan | keyUp)], [INPUT.Keyboard(0, 0x2A, scan | keyUp), INPUT.Keyboard(0, 0x38, scan | keyUp)]),
            _ => throw new ArgumentOutOfRangeException(nameof(shortcut))
        };
        SendInputSequence(sequence.Inputs, sequence.Compensation);
    }
    private void SendInputSequence(INPUT[] inputs, INPUT[] compensation)
    {
        try { _sender.Send(inputs); }
        catch
        {
            try { _sender.Send(compensation); }
            catch { }
            throw;
        }
    }
    public void MovePointer(int dx, int dy) => _sender.Send([INPUT.Mouse(dx, dy, 0, 0x0001)]);
    public void SetPointerButton(MouseButton button, ButtonAction action)
    {
        var flags = (button, action) switch { (MouseButton.Left, ButtonAction.Down) => 0x0002u, (MouseButton.Left, ButtonAction.Up) => 0x0004u, (MouseButton.Right, ButtonAction.Down) => 0x0008u, (MouseButton.Right, ButtonAction.Up) => 0x0010u, _ => throw new ArgumentOutOfRangeException() };
        _sender.Send([INPUT.Mouse(0, 0, 0, flags)]);
    }
    public void Wheel(int delta) => _sender.Send([INPUT.Mouse(0, 0, unchecked((uint)delta), 0x0800)]);
}

[StructLayout(LayoutKind.Sequential)]
public struct INPUT
{
    public uint type;
    public InputUnion U;
    public static INPUT Keyboard(ushort vk, ushort scan, uint flags) => new() { type = 1, U = new() { ki = new() { wVk = vk, wScan = scan, dwFlags = flags } } };
    public static INPUT Mouse(int dx, int dy, uint data, uint flags) => new() { type = 0, U = new() { mi = new() { dx = dx, dy = dy, mouseData = data, dwFlags = flags } } };
}
[StructLayout(LayoutKind.Explicit)] public struct InputUnion { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
[StructLayout(LayoutKind.Sequential)] public struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public UIntPtr dwExtraInfo; }
[StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public UIntPtr dwExtraInfo; }
