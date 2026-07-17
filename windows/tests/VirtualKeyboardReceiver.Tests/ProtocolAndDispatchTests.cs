using System.Text;
using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class ProtocolAndDispatchTests
{
    [Fact]
    public void AuthProof_MatchesContract_AndWrongCodeDoesNot()
    {
        const string nonce = "YWJjZA==";
        const string fingerprint = "AABBCC";
        var proof = Authentication.ComputeProof("ABCD-EFGH-IJKL-MNOP", nonce, fingerprint);
        Assert.Equal(proof.ToUpperInvariant(), proof);
        Assert.True(Authentication.VerifyProof("ABCDEFGHIJKLMNOP", nonce, fingerprint, proof));
        Assert.False(Authentication.VerifyProof("ABCDEFGHIJKLMNOP", nonce, fingerprint, new string('0', 64)));
    }

    public static TheoryData<string, Type> Commands => new()
    {
        { Json("textCommit", "\"text\":\"A😀\""), typeof(TextCommitCommand) },
        { Json("replaceTail", "\"deleteCodePoints\":2,\"text\":\"x\""), typeof(ReplaceTailCommand) },
        { Json("keyPress", "\"key\":\"left\""), typeof(KeyPressCommand) },
        { Json("keyPress", "\"key\":\"escape\""), typeof(KeyPressCommand) },
        { Json("keyState", "\"key\":\"w\",\"action\":\"down\""), typeof(KeyStateCommand) },
        { Json("systemShortcut", "\"shortcut\":\"shift\""), typeof(SystemShortcutCommand) },
        { Json("systemShortcut", "\"shortcut\":\"altShift\""), typeof(SystemShortcutCommand) },
        { Json("pointerMove", "\"dx\":3,\"dy\":-4"), typeof(PointerMoveCommand) },
        { Json("pointerButton", "\"button\":\"right\",\"action\":\"down\""), typeof(PointerButtonCommand) },
        { Json("wheel", "\"delta\":120"), typeof(WheelCommand) },
        { Json("ping", ""), typeof(PingCommand) },
    };

    [Theory]
    [InlineData("shift", SystemShortcut.Shift)]
    [InlineData("controlSpace", SystemShortcut.ControlSpace)]
    [InlineData("capsLock", SystemShortcut.CapsLock)]
    [InlineData("windowsSpace", SystemShortcut.WindowsSpace)]
    [InlineData("controlShift", SystemShortcut.ControlShift)]
    [InlineData("altShift", SystemShortcut.AltShift)]
    public void Parser_MapsEveryAllowedSystemShortcut(string shortcut, SystemShortcut expected)
    {
        var command = Assert.IsType<SystemShortcutCommand>(ProtocolParser.ParseCommand(
            Encoding.UTF8.GetBytes(Json("systemShortcut", $"\"shortcut\":\"{shortcut}\""))));

        Assert.Equal(expected, command.Shortcut);
    }

    [Theory]
    [MemberData(nameof(Commands))]
    public void Parser_ParsesEveryCommand(string json, Type expected) =>
        Assert.IsType(expected, ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(json)));

    [Fact]
    public void Parser_OptionalPreferPhysicalKeysDefaultsFalseAndAcceptsTrue()
    {
        var text = Assert.IsType<TextCommitCommand>(ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(Json("textCommit", "\"text\":\"A\""))));
        var physicalText = Assert.IsType<TextCommitCommand>(ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(Json("textCommit", "\"text\":\"A\",\"preferPhysicalKeys\":true"))));
        var physicalReplace = Assert.IsType<ReplaceTailCommand>(ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(Json("replaceTail", "\"deleteCodePoints\":1,\"text\":\"A\",\"preferPhysicalKeys\":true"))));

        Assert.False(text.PreferPhysicalKeys);
        Assert.True(physicalText.PreferPhysicalKeys);
        Assert.True(physicalReplace.PreferPhysicalKeys);
    }

    [Theory]
    [InlineData("{bad")]
    [InlineData("{\"version\":2,\"type\":\"ping\",\"seq\":1,\"timestamp\":2}")]
    [InlineData("{\"version\":1,\"type\":\"unknown\",\"seq\":1,\"timestamp\":2}")]
    [InlineData("{\"version\":1,\"type\":\"ping\",\"seq\":1,\"timestamp\":2,\"extra\":true}")]
    [InlineData("{\"version\":1,\"type\":\"keyPress\",\"seq\":1,\"timestamp\":2,\"key\":\"escape\",\"preferPhysicalKeys\":true}")]
    [InlineData("{\"version\":1,\"type\":\"textCommit\",\"seq\":1,\"timestamp\":2,\"text\":\"A\",\"preferPhysicalKeys\":1}")]
    [InlineData("{\"version\":1,\"type\":\"textCommit\",\"seq\":1,\"timestamp\":2,\"text\":\"A\",\"preferPhysicalKeys\":false,\"preferPhysicalKeys\":true}")]
    [InlineData("{\"version\":1,\"type\":\"keyPress\",\"seq\":1,\"timestamp\":2,\"key\":\"space\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"x\",\"action\":\"down\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"w\",\"action\":\"hold\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":1,\"action\":\"down\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"w\",\"action\":\"down\",\"extra\":true}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"w\",\"action\":\"down\",\"action\":\"up\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"W\",\"action\":\"down\"}")]
    [InlineData("{\"version\":1,\"type\":\"keyState\",\"seq\":1,\"timestamp\":2,\"key\":\"w\",\"action\":\"DOWN\"}")]
    [InlineData("{\"version\":1,\"type\":\"systemShortcut\",\"seq\":1,\"timestamp\":2,\"shortcut\":\"unknown\"}")]
    [InlineData("{\"version\":1,\"type\":\"systemShortcut\",\"seq\":1,\"timestamp\":2}")]
    [InlineData("{\"version\":1,\"type\":\"systemShortcut\",\"seq\":1,\"timestamp\":2,\"shortcut\":1}")]
    [InlineData("{\"version\":1,\"type\":\"systemShortcut\",\"seq\":1,\"timestamp\":2,\"shortcut\":\"shift\",\"extra\":true}")]
    [InlineData("{\"version\":1,\"type\":\"systemShortcut\",\"seq\":1,\"timestamp\":2,\"shortcut\":\"shift\",\"shortcut\":\"capsLock\"}")]
    public void Parser_RejectsMalformedWrongVersionUnknownAndUnknownFields(string json) =>
        Assert.Throws<ProtocolException>(() => ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(json)));

    [Theory]
    [InlineData(-1)]
    [InlineData(1025)]
    [InlineData(int.MaxValue)]
    public void Parser_RejectsReplaceTailDeleteCountsOutsideBound(int deleteCodePoints)
    {
        var json = Json("replaceTail", $"\"deleteCodePoints\":{deleteCodePoints},\"text\":\"x\"");

        Assert.Throws<ProtocolException>(() => ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(json)));
    }

    [Fact]
    public void Parser_AcceptsReplaceTailAtPublishedMaximum()
    {
        var json = Json("replaceTail", $"\"deleteCodePoints\":{ProtocolConstants.MaxReplaceTailCodePoints},\"text\":\"x\"");

        var command = Assert.IsType<ReplaceTailCommand>(ProtocolParser.ParseCommand(Encoding.UTF8.GetBytes(json)));
        Assert.Equal(ProtocolConstants.MaxReplaceTailCodePoints, command.DeleteCodePoints);
    }

    [Fact]
    public async Task FrameReader_RejectsOversizeAndInvalidUtf8()
    {
        await Assert.ThrowsAsync<ProtocolException>(() => JsonLineFraming.ReadLineAsync(
            new MemoryStream(Enumerable.Repeat((byte)'x', ProtocolConstants.MaxLineBytes + 1).Append((byte)'\n').ToArray()), default).AsTask());
        await Assert.ThrowsAsync<ProtocolException>(() => JsonLineFraming.ReadLineAsync(
            new MemoryStream(new byte[] { 0xff, (byte)'\n' }), default).AsTask());
    }

    [Fact]
    public void Dispatcher_MapsTextEmojiReplaceKeysPointerAndWheel_AndReleasesHeldButtons()
    {
        var fake = new RecordingInjector();
        var dispatcher = new CommandDispatcher(fake);
        dispatcher.Dispatch(new TextCommitCommand(1, 2, "A😀"));
        dispatcher.Dispatch(new ReplaceTailCommand(2, 3, 2, "Z"));
        foreach (var key in Enum.GetValues<ReceiverKey>()) dispatcher.Dispatch(new KeyPressCommand(3, 4, key));
        dispatcher.Dispatch(new PointerMoveCommand(4, 5, 9, -7));
        dispatcher.Dispatch(new PointerButtonCommand(5, 6, MouseButton.Left, ButtonAction.Down));
        dispatcher.Dispatch(new WheelCommand(6, 7, -120));
        dispatcher.ReleaseHeldInputs();

        Assert.Contains("text:A😀", fake.Events);
        Assert.Contains("key:Backspace", fake.Events);
        Assert.Equal(3, fake.Events.Count(e => e == "key:Backspace")); // two replacement deletes + key enum
        Assert.Contains("move:9,-7", fake.Events);
        Assert.Contains("wheel:-120", fake.Events);
        Assert.EndsWith("button:Left,Up", fake.Events[^1]);
    }

    [Fact]
    public void Dispatcher_PreservesPreferPhysicalKeysForTextAndReplaceTail()
    {
        var fake = new RecordingInjector();
        var dispatcher = new CommandDispatcher(fake);

        dispatcher.Dispatch(new TextCommitCommand(1, 2, "A", true));
        dispatcher.Dispatch(new ReplaceTailCommand(2, 3, 1, "B", true));

        Assert.Equal([true, true], fake.TextPreferences);
    }

    [Fact]
    public void DispatcherPassesSessionCancellationToTextAndReplaceInjection()
    {
        var fake = new RecordingInjector();
        var dispatcher = new CommandDispatcher(fake);
        fake.ThrowIfTextCancellationRequested = true;
        using var cancelled = new CancellationTokenSource();
        cancelled.Cancel();

        Assert.Throws<OperationCanceledException>(() => dispatcher.Dispatch(
            new TextCommitCommand(1, 2, "A"), cancelled.Token));

        fake.ThrowIfTextCancellationRequested = false;
        using var active = new CancellationTokenSource();
        dispatcher.Dispatch(new ReplaceTailCommand(2, 3, 0, "B"), active.Token);

        Assert.Equal([true, false], fake.TextCancellationStates);
    }

    [Fact]
    public void Dispatcher_KeyStateDownUpIsIdempotentAndTracksHeldState()
    {
        var fake = new RecordingInjector();
        var dispatcher = new CommandDispatcher(fake);

        dispatcher.Dispatch(new KeyStateCommand(1, 2, GameKey.W, ButtonAction.Down));
        dispatcher.Dispatch(new KeyStateCommand(2, 3, GameKey.W, ButtonAction.Down));
        dispatcher.Dispatch(new KeyStateCommand(3, 4, GameKey.W, ButtonAction.Up));
        dispatcher.Dispatch(new KeyStateCommand(4, 5, GameKey.W, ButtonAction.Up));

        Assert.Equal(["game:W,Down", "game:W,Up"], fake.Events.Where(e => e.StartsWith("game:")).ToArray());
    }

    [Fact]
    public void Dispatcher_DispatchesSystemShortcut()
    {
        var fake = new RecordingInjector();
        new CommandDispatcher(fake).Dispatch(new SystemShortcutCommand(1, 2, SystemShortcut.ControlShift));

        Assert.Contains("shortcut:ControlShift", fake.Events);
    }

    [Fact]
    public void Dispatcher_DownFailureDoesNotHoldAndUpFailureRemainsForCleanupRetry()
    {
        var injector = new StatefulGameInjector();
        var dispatcher = new CommandDispatcher(injector);

        injector.ThrowOnDown = true;
        Assert.Throws<InvalidOperationException>(() => dispatcher.Dispatch(new KeyStateCommand(1, 2, GameKey.A, ButtonAction.Down)));
        injector.ThrowOnDown = false;
        dispatcher.Dispatch(new KeyStateCommand(2, 3, GameKey.A, ButtonAction.Down));

        injector.ThrowOnUp = true;
        Assert.Throws<InvalidOperationException>(() => dispatcher.Dispatch(new KeyStateCommand(3, 4, GameKey.A, ButtonAction.Up)));
        injector.ThrowOnUp = false;
        dispatcher.ReleaseHeldInputs();

        Assert.Equal(2, injector.DownCount);
        Assert.Equal(2, injector.UpCount);
    }

    [Fact]
    public void Dispatcher_ReleaseHeldInputsContinuesAfterEachReleaseFailureAndClearsState()
    {
        var injector = new MultiFailureInjector();
        var dispatcher = new CommandDispatcher(injector);
        dispatcher.Dispatch(new PointerButtonCommand(1, 2, MouseButton.Left, ButtonAction.Down));
        dispatcher.Dispatch(new KeyStateCommand(2, 3, GameKey.D, ButtonAction.Down));

        var firstFailure = Assert.Throws<InvalidOperationException>(() => dispatcher.ReleaseHeldInputs());
        dispatcher.ReleaseHeldInputs();

        Assert.Equal("mouse release failed", firstFailure.Message);
        Assert.Equal(["mouse:Left,Down", "game:D,Down", "mouse:Left,Up", "game:D,Up"], injector.Events);
    }

    [Fact]
    public async Task Dispatcher_CancellationStopsReplaceTailBeforeRemainingDeletesAndTextCommit()
    {
        var injector = new BlockingBackspaceInjector();
        var dispatcher = new CommandDispatcher(injector);
        using var cancellation = new CancellationTokenSource();
        var dispatch = Task.Run(() => dispatcher.Dispatch(
            new ReplaceTailCommand(1, 2, ProtocolConstants.MaxReplaceTailCodePoints, "replacement"),
            cancellation.Token));
        Assert.True(injector.FirstBackspaceEntered.Wait(TimeSpan.FromSeconds(2)));

        cancellation.Cancel();
        injector.Continue.Set();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => dispatch);
        Assert.Equal(1, injector.BackspaceCount);
        Assert.False(injector.TextCommitted);
    }

    private static string Json(string type, string fields) =>
        $"{{\"version\":1,\"type\":\"{type}\",\"seq\":7,\"timestamp\":8{(fields.Length == 0 ? "" : "," + fields)}}}";

    private sealed class RecordingInjector : IInputInjector
    {
        public List<string> Events { get; } = [];
        public List<bool> TextPreferences { get; } = [];
        public List<bool> TextCancellationStates { get; } = [];
        public bool ThrowIfTextCancellationRequested { get; set; }
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) { Events.Add($"text:{text}"); TextPreferences.Add(preferPhysicalKeys); TextCancellationStates.Add(cancellationToken.IsCancellationRequested); if (ThrowIfTextCancellationRequested) cancellationToken.ThrowIfCancellationRequested(); }
        public void PressKey(ReceiverKey key) => Events.Add($"key:{key}");
        public void MovePointer(int dx, int dy) => Events.Add($"move:{dx},{dy}");
        public void SetPointerButton(MouseButton button, ButtonAction action) => Events.Add($"button:{button},{action}");
        public void SetKeyState(GameKey key, ButtonAction action) => Events.Add($"game:{key},{action}");
        public void PressSystemShortcut(SystemShortcut shortcut) => Events.Add($"shortcut:{shortcut}");
        public void Wheel(int delta) => Events.Add($"wheel:{delta}");
    }

    private sealed class BlockingBackspaceInjector : IInputInjector
    {
        public ManualResetEventSlim FirstBackspaceEntered { get; } = new();
        public ManualResetEventSlim Continue { get; } = new();
        public int BackspaceCount { get; private set; }
        public bool TextCommitted { get; private set; }
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) => TextCommitted = true;
        public void PressKey(ReceiverKey key)
        {
            if (key != ReceiverKey.Backspace) return;
            BackspaceCount++;
            if (BackspaceCount == 1)
            {
                FirstBackspaceEntered.Set();
                Continue.Wait(TimeSpan.FromSeconds(2));
            }
        }
        public void MovePointer(int dx, int dy) { }
        public void SetPointerButton(MouseButton button, ButtonAction action) { }
        public void SetKeyState(GameKey key, ButtonAction action) { }
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void Wheel(int delta) { }
    }

    private sealed class StatefulGameInjector : IInputInjector
    {
        public bool ThrowOnDown { get; set; }
        public bool ThrowOnUp { get; set; }
        public int DownCount { get; private set; }
        public int UpCount { get; private set; }
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) { }
        public void PressKey(ReceiverKey key) { }
        public void MovePointer(int dx, int dy) { }
        public void SetPointerButton(MouseButton button, ButtonAction action) { }
        public void SetKeyState(GameKey key, ButtonAction action)
        {
            if (action == ButtonAction.Down)
            {
                DownCount++;
                if (ThrowOnDown) throw new InvalidOperationException("down failed");
            }
            else
            {
                UpCount++;
                if (ThrowOnUp) throw new InvalidOperationException("up failed");
            }
        }
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void Wheel(int delta) { }
    }

    private sealed class MultiFailureInjector : IInputInjector
    {
        public List<string> Events { get; } = [];
        public void InjectText(string text, bool preferPhysicalKeys, CancellationToken cancellationToken = default) { }
        public void PressKey(ReceiverKey key) { }
        public void MovePointer(int dx, int dy) { }
        public void SetPointerButton(MouseButton button, ButtonAction action)
        {
            Events.Add($"mouse:{button},{action}");
            if (action == ButtonAction.Up) throw new InvalidOperationException("mouse release failed");
        }
        public void SetKeyState(GameKey key, ButtonAction action)
        {
            Events.Add($"game:{key},{action}");
            if (action == ButtonAction.Up) throw new InvalidOperationException("game release failed");
        }
        public void PressSystemShortcut(SystemShortcut shortcut) { }
        public void Wheel(int delta) { }
    }
}
