namespace VirtualKeyboardReceiver.Core;

public abstract record ReceiverCommand(long Seq, long Timestamp);
public sealed record TextCommitCommand(long Seq, long Timestamp, string Text, bool PreferPhysicalKeys = false) : ReceiverCommand(Seq, Timestamp);
public sealed record ReplaceTailCommand(long Seq, long Timestamp, int DeleteCodePoints, string Text, bool PreferPhysicalKeys = false) : ReceiverCommand(Seq, Timestamp);
public sealed record KeyPressCommand(long Seq, long Timestamp, ReceiverKey Key) : ReceiverCommand(Seq, Timestamp);
public sealed record KeyStateCommand(long Seq, long Timestamp, GameKey Key, ButtonAction Action) : ReceiverCommand(Seq, Timestamp);
public sealed record SystemShortcutCommand(long Seq, long Timestamp, SystemShortcut Shortcut) : ReceiverCommand(Seq, Timestamp);
public sealed record ShortcutChordCommand(long Seq, long Timestamp, IReadOnlyList<ShortcutModifier> Modifiers, ShortcutKey Key) : ReceiverCommand(Seq, Timestamp);
public sealed record PointerMoveCommand(long Seq, long Timestamp, int Dx, int Dy) : ReceiverCommand(Seq, Timestamp);
public sealed record PointerButtonCommand(long Seq, long Timestamp, MouseButton Button, ButtonAction Action) : ReceiverCommand(Seq, Timestamp);
public sealed record WheelCommand(long Seq, long Timestamp, int Delta) : ReceiverCommand(Seq, Timestamp);
public sealed record PingCommand(long Seq, long Timestamp) : ReceiverCommand(Seq, Timestamp);

public enum ReceiverKey { Up, Down, Left, Right, Backspace, Enter, Escape }
public enum GameKey { W, A, S, D }
public enum SystemShortcut { Shift, ControlSpace, CapsLock, WindowsSpace, ControlShift, AltShift }
public enum ShortcutModifier { Shift, Control, Alt, Meta }
public enum ShortcutSpecialKey { Space, Enter, Backspace }
public abstract record ShortcutKey
{
    public sealed record Character(char Value) : ShortcutKey;
    public sealed record Special(ShortcutSpecialKey Value) : ShortcutKey;
}
public enum MouseButton { Left, Right }
public enum ButtonAction { Down, Up }
