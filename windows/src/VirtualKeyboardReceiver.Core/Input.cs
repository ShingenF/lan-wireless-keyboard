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
    void PressShortcutChord(IReadOnlyList<ShortcutModifier> modifiers, ShortcutKey key);
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
            case ShortcutChordCommand c: injector.PressShortcutChord(c.Modifiers, c.Key); break;
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
        var modifiers = new List<(ushort ScanCode, bool Extended)>();
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Shift)) modifiers.Add((0x2A, false));
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Control)) modifiers.Add((0x1D, false));
        if (stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Alt)) modifiers.Add((0x38, stroke.Modifiers.HasFlag(PhysicalKeyModifiers.Control)));
        SendPhysicalStroke(stroke, modifiers);
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
    public void PressShortcutChord(IReadOnlyList<ShortcutModifier> modifiers, ShortcutKey key)
    {
        if (modifiers.Count == 0 || modifiers.Distinct().Count() != modifiers.Count)
            throw new ArgumentException("Shortcut modifiers must be non-empty and unique.", nameof(modifiers));

        var stroke = key switch
        {
            ShortcutKey.Character character when _physicalKeyMapper.TryMap(character.Value, out var mapped) => mapped,
            ShortcutKey.Character character when TryMapUsAscii(character.Value, out var fallback) => fallback,
            ShortcutKey.Character => throw new ArgumentException("Shortcut character is not mappable.", nameof(key)),
            ShortcutKey.Special { Value: ShortcutSpecialKey.Space } =>
                new PhysicalKeyStroke(0, 0x39, PhysicalKeyModifiers.None, false),
            ShortcutKey.Special { Value: ShortcutSpecialKey.Enter } =>
                new PhysicalKeyStroke(0, 0x1C, PhysicalKeyModifiers.None, false),
            ShortcutKey.Special { Value: ShortcutSpecialKey.Backspace } =>
                new PhysicalKeyStroke(0, 0x0E, PhysicalKeyModifiers.None, false),
            _ => throw new ArgumentOutOfRangeException(nameof(key))
        };

        var requested = modifiers.ToHashSet();
        var intrinsic = stroke.Modifiers;
        var altGr = intrinsic.HasFlag(PhysicalKeyModifiers.Control) && intrinsic.HasFlag(PhysicalKeyModifiers.Alt);
        var modifierKeys = new List<(ushort ScanCode, bool Extended)>();
        if (requested.Contains(ShortcutModifier.Meta)) modifierKeys.Add((0x5B, true));
        if (requested.Contains(ShortcutModifier.Control) || intrinsic.HasFlag(PhysicalKeyModifiers.Control))
            modifierKeys.Add((0x1D, false));
        if (requested.Contains(ShortcutModifier.Alt) || intrinsic.HasFlag(PhysicalKeyModifiers.Alt))
            modifierKeys.Add((0x38, altGr));
        if (requested.Contains(ShortcutModifier.Shift) || intrinsic.HasFlag(PhysicalKeyModifiers.Shift))
            modifierKeys.Add((0x2A, false));

        SendPhysicalStroke(stroke, modifierKeys);
    }
    private void SendPhysicalStroke(
        PhysicalKeyStroke stroke,
        IReadOnlyList<(ushort ScanCode, bool Extended)> modifierKeys)
    {
        const uint scan = 0x0008;
        const uint extendedScan = 0x0009;
        const uint keyUp = 0x0002;
        var inputs = new List<INPUT>();
        foreach (var modifier in modifierKeys)
            inputs.Add(INPUT.Keyboard(0, modifier.ScanCode, modifier.Extended ? extendedScan : scan));
        var keyFlags = scan | (stroke.Extended ? 0x0001u : 0u);
        inputs.Add(INPUT.Keyboard(0, stroke.ScanCode, keyFlags));
        inputs.Add(INPUT.Keyboard(0, stroke.ScanCode, keyFlags | keyUp));
        for (var index = modifierKeys.Count - 1; index >= 0; index--)
        {
            var modifier = modifierKeys[index];
            inputs.Add(INPUT.Keyboard(0, modifier.ScanCode, (modifier.Extended ? extendedScan : scan) | keyUp));
        }

        var compensation = new List<INPUT> { INPUT.Keyboard(0, stroke.ScanCode, keyFlags | keyUp) };
        for (var index = modifierKeys.Count - 1; index >= 0; index--)
        {
            var modifier = modifierKeys[index];
            compensation.Add(INPUT.Keyboard(0, modifier.ScanCode, (modifier.Extended ? extendedScan : scan) | keyUp));
        }
        SendInputSequence(inputs.ToArray(), compensation.ToArray());
    }
    private static bool TryMapUsAscii(char character, out PhysicalKeyStroke stroke)
    {
        stroke = default;
        ushort scanCode;
        var modifiers = PhysicalKeyModifiers.None;
        if (character is >= 'a' and <= 'z' or >= 'A' and <= 'Z')
        {
            scanCode = char.ToUpperInvariant(character) switch
            {
                'A' => 0x1E, 'B' => 0x30, 'C' => 0x2E, 'D' => 0x20, 'E' => 0x12,
                'F' => 0x21, 'G' => 0x22, 'H' => 0x23, 'I' => 0x17, 'J' => 0x24,
                'K' => 0x25, 'L' => 0x26, 'M' => 0x32, 'N' => 0x31, 'O' => 0x18,
                'P' => 0x19, 'Q' => 0x10, 'R' => 0x13, 'S' => 0x1F, 'T' => 0x14,
                'U' => 0x16, 'V' => 0x2F, 'W' => 0x11, 'X' => 0x2D, 'Y' => 0x15,
                'Z' => 0x2C, _ => 0
            };
            if (char.IsUpper(character)) modifiers = PhysicalKeyModifiers.Shift;
        }
        else
        {
            (ushort ScanCode, bool Shifted) mapping = character switch
            {
                '1' => (0x02, false), '2' => (0x03, false), '3' => (0x04, false),
                '4' => (0x05, false), '5' => (0x06, false), '6' => (0x07, false),
                '7' => (0x08, false), '8' => (0x09, false), '9' => (0x0A, false),
                '0' => (0x0B, false), '-' => (0x0C, false), '=' => (0x0D, false),
                '[' => (0x1A, false), ']' => (0x1B, false), '\\' => (0x2B, false),
                ';' => (0x27, false), '\'' => (0x28, false), '`' => (0x29, false),
                ',' => (0x33, false), '.' => (0x34, false), '/' => (0x35, false),
                '!' => (0x02, true), '@' => (0x03, true), '#' => (0x04, true),
                '$' => (0x05, true), '%' => (0x06, true), '^' => (0x07, true),
                '&' => (0x08, true), '*' => (0x09, true), '(' => (0x0A, true),
                ')' => (0x0B, true), '_' => (0x0C, true), '+' => (0x0D, true),
                '{' => (0x1A, true), '}' => (0x1B, true), '|' => (0x2B, true),
                ':' => (0x27, true), '"' => (0x28, true), '~' => (0x29, true),
                '<' => (0x33, true), '>' => (0x34, true), '?' => (0x35, true),
                _ => (0, false)
            };
            scanCode = mapping.ScanCode;
            if (mapping.Shifted) modifiers = PhysicalKeyModifiers.Shift;
        }
        if (scanCode == 0) return false;
        stroke = new PhysicalKeyStroke(0, scanCode, modifiers, false);
        return true;
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
