using System.Buffers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace VirtualKeyboardReceiver.Core;

public static class ProtocolConstants
{
    public const int Version = 1;
    public const int DefaultPort = 39421;
    public const int MaxLineBytes = 65_536;
    public const int MaxReplaceTailCodePoints = 1_024;
}

public sealed class ProtocolException(string message) : Exception(message);

public static class Authentication
{
    public static string ComputeProof(string pairingCode, string nonce, string fingerprint)
    {
        var key = Encoding.UTF8.GetBytes(pairingCode.Replace("-", "", StringComparison.Ordinal).ToUpperInvariant());
        var data = Encoding.UTF8.GetBytes(nonce + ":" + fingerprint);
        return Convert.ToHexString(HMACSHA256.HashData(key, data));
    }

    public static bool VerifyProof(string pairingCode, string nonce, string fingerprint, string proof)
    {
        if (proof.Length != 64) return false;
        try
        {
            return CryptographicOperations.FixedTimeEquals(
                Convert.FromHexString(ComputeProof(pairingCode, nonce, fingerprint)),
                Convert.FromHexString(proof));
        }
        catch (FormatException) { return false; }
    }
}

public static class JsonLineFraming
{
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);

    public static async ValueTask<string?> ReadLineAsync(Stream stream, CancellationToken cancellationToken)
    {
        var buffer = new ArrayBufferWriter<byte>();
        var one = new byte[1];
        while (true)
        {
            var count = await stream.ReadAsync(one, cancellationToken).ConfigureAwait(false);
            if (count == 0)
            {
                if (buffer.WrittenCount == 0) return null;
                throw new ProtocolException("Unexpected end of stream before newline.");
            }
            if (one[0] == (byte)'\n') break;
            if (buffer.WrittenCount >= ProtocolConstants.MaxLineBytes) throw new ProtocolException("Frame exceeds 65536 bytes.");
            buffer.GetSpan(1)[0] = one[0];
            buffer.Advance(1);
        }
        var bytes = buffer.WrittenSpan;
        if (bytes.Length > 0 && bytes[^1] == (byte)'\r') bytes = bytes[..^1];
        try { return StrictUtf8.GetString(bytes); }
        catch (DecoderFallbackException) { throw new ProtocolException("Frame is not valid UTF-8."); }
    }

    public static async Task WriteAsync(Stream stream, object message, CancellationToken cancellationToken)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(message);
        await stream.WriteAsync(bytes, cancellationToken).ConfigureAwait(false);
        await stream.WriteAsync("\n"u8.ToArray(), cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }
}

public static class ProtocolParser
{
    public static ReceiverCommand ParseCommand(ReadOnlySpan<byte> utf8)
    {
        JsonDocument doc;
        try { doc = JsonDocument.Parse(utf8.ToArray()); }
        catch (JsonException ex) { throw new ProtocolException("Malformed JSON: " + ex.Message); }
        using (doc)
        {
            var root = RequireObject(doc.RootElement);
            var version = RequiredInt32(root, "version");
            if (version != 1) throw new ProtocolException("Unsupported version.");
            var type = RequiredString(root, "type");
            var seq = RequiredInt64(root, "seq");
            var timestamp = RequiredInt64(root, "timestamp");
            return type switch
            {
                "textCommit" => Build(root, ["version", "type", "seq", "timestamp", "text", "preferPhysicalKeys"],
                    () => new TextCommitCommand(seq, timestamp, RequiredString(root, "text"), OptionalBoolean(root, "preferPhysicalKeys")), ["preferPhysicalKeys"]),
                "replaceTail" => Build(root, ["version", "type", "seq", "timestamp", "deleteCodePoints", "text", "preferPhysicalKeys"],
                    () => new ReplaceTailCommand(seq, timestamp, ReplaceTailDeleteCount(root), RequiredString(root, "text"), OptionalBoolean(root, "preferPhysicalKeys")), ["preferPhysicalKeys"]),
                "keyPress" => Build(root, ["version", "type", "seq", "timestamp", "key"],
                    () => new KeyPressCommand(seq, timestamp, ParseEnum<ReceiverKey>(RequiredString(root, "key")))),
                "keyState" => Build(root, ["version", "type", "seq", "timestamp", "key", "action"],
                    () => new KeyStateCommand(seq, timestamp, ParseGameKey(RequiredString(root, "key")), ParseGameAction(RequiredString(root, "action")))),
                "systemShortcut" => Build(root, ["version", "type", "seq", "timestamp", "shortcut"],
                    () => new SystemShortcutCommand(seq, timestamp, ParseSystemShortcut(RequiredString(root, "shortcut")))),
                "pointerMove" => Build(root, ["version", "type", "seq", "timestamp", "dx", "dy"],
                    () => new PointerMoveCommand(seq, timestamp, RequiredInt32(root, "dx"), RequiredInt32(root, "dy"))),
                "pointerButton" => Build(root, ["version", "type", "seq", "timestamp", "button", "action"],
                    () => new PointerButtonCommand(seq, timestamp, ParseEnum<MouseButton>(RequiredString(root, "button")), ParseEnum<ButtonAction>(RequiredString(root, "action")))),
                "wheel" => Build(root, ["version", "type", "seq", "timestamp", "delta"],
                    () => new WheelCommand(seq, timestamp, RequiredInt32(root, "delta"))),
                "ping" => Build(root, ["version", "type", "seq", "timestamp"], () => new PingCommand(seq, timestamp)),
                _ => throw new ProtocolException("Unknown command type.")
            };
        }
    }

    public static (string Proof, string Device) ParseAuth(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = RequireObject(doc.RootElement);
            Exact(root, ["version", "type", "proof", "device"]);
            if (RequiredInt32(root, "version") != 1 || RequiredString(root, "type") != "auth") throw new ProtocolException("Expected v1 auth.");
            return (RequiredString(root, "proof"), RequiredString(root, "device"));
        }
        catch (JsonException ex) { throw new ProtocolException("Malformed JSON: " + ex.Message); }
    }

    private static T Build<T>(JsonElement root, string[] fields, Func<T> factory, string[]? optional = null) { Exact(root, fields, optional ?? []); return factory(); }
    private static JsonElement RequireObject(JsonElement value) => value.ValueKind == JsonValueKind.Object ? value : throw new ProtocolException("JSON root must be an object.");
    private static void Exact(JsonElement root, string[] allowed, string[]? optional = null)
    {
        var set = allowed.ToHashSet(StringComparer.Ordinal);
        var optionalSet = (optional ?? []).ToHashSet(StringComparer.Ordinal);
        var seen = new HashSet<string>(StringComparer.Ordinal);
        foreach (var p in root.EnumerateObject())
            if (!set.Contains(p.Name) || !seen.Add(p.Name)) throw new ProtocolException("Unknown or duplicate field: " + p.Name);
        if (!set.Except(optionalSet).All(seen.Contains)) throw new ProtocolException("Missing required field.");
    }
    private static string RequiredString(JsonElement root, string name) => root.TryGetProperty(name, out var p) && p.ValueKind == JsonValueKind.String ? p.GetString()! : throw new ProtocolException(name + " must be a string.");
    private static long RequiredInt64(JsonElement root, string name) => root.TryGetProperty(name, out var p) && p.TryGetInt64(out var n) ? n : throw new ProtocolException(name + " must be an Int64.");
    private static int RequiredInt32(JsonElement root, string name) => root.TryGetProperty(name, out var p) && p.TryGetInt32(out var n) ? n : throw new ProtocolException(name + " must be an Int32.");
    private static bool OptionalBoolean(JsonElement root, string name) => !root.TryGetProperty(name, out var p) || p.ValueKind == JsonValueKind.False ? false : p.ValueKind == JsonValueKind.True ? true : throw new ProtocolException(name + " must be a Boolean.");
    private static GameKey ParseGameKey(string value) => value switch
    {
        "w" => GameKey.W,
        "a" => GameKey.A,
        "s" => GameKey.S,
        "d" => GameKey.D,
        _ => throw new ProtocolException("Invalid game key.")
    };
    private static ButtonAction ParseGameAction(string value) => value switch
    {
        "down" => ButtonAction.Down,
        "up" => ButtonAction.Up,
        _ => throw new ProtocolException("Invalid game key action.")
    };
    private static SystemShortcut ParseSystemShortcut(string value) => value switch
    {
        "shift" => SystemShortcut.Shift,
        "controlSpace" => SystemShortcut.ControlSpace,
        "capsLock" => SystemShortcut.CapsLock,
        "windowsSpace" => SystemShortcut.WindowsSpace,
        "controlShift" => SystemShortcut.ControlShift,
        "altShift" => SystemShortcut.AltShift,
        _ => throw new ProtocolException("Invalid system shortcut.")
    };
    private static int NonNegativeInt32(JsonElement root, string name) { var n = RequiredInt32(root, name); return n >= 0 ? n : throw new ProtocolException(name + " must not be negative."); }
    private static int ReplaceTailDeleteCount(JsonElement root)
    {
        var count = NonNegativeInt32(root, "deleteCodePoints");
        return count <= ProtocolConstants.MaxReplaceTailCodePoints
            ? count
            : throw new ProtocolException($"deleteCodePoints must not exceed {ProtocolConstants.MaxReplaceTailCodePoints}.");
    }
    private static T ParseEnum<T>(string value) where T : struct, Enum => Enum.TryParse<T>(value, true, out var parsed) && string.Equals(parsed.ToString(), value, StringComparison.OrdinalIgnoreCase) ? parsed : throw new ProtocolException("Invalid enum value.");
}
