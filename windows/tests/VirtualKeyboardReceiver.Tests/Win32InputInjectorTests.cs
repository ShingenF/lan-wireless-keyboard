using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class Win32InputInjectorTests
{
    [Fact]
    public void UnicodeUsesEveryUtf16CodeUnitIncludingSurrogatePair()
    {
        var sender = new RecordingSender();
        new Win32InputInjector(sender).InjectText("A😀");

        Assert.Equal(3, sender.Batches.Count);
        AssertUnicodeBatch(sender.Batches[0], 'A');
        AssertUnicodeBatch(sender.Batches[1], (char)0xD83D);
        AssertUnicodeBatch(sender.Batches[2], (char)0xDE00);
    }

    [Fact]
    public void TextInjectionObservesCancellationBetweenCodeUnits()
    {
        using var cancellation = new CancellationTokenSource();
        var sender = new CancellingSender(cancellation, cancelAfterSends: 1);

        Assert.Throws<OperationCanceledException>(() =>
            new Win32InputInjector(sender, textInputPacer: new NoOpPacer())
                .InjectText("ABC", cancellationToken: cancellation.Token));

        Assert.Single(sender.Batches);
    }

    [Fact]
    public void PreferPhysicalKeysUsesOneAtomicScanCodeBatchAndReleasesModifiersInReverseOrder()
    {
        var sender = new RecordingSender();
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('A', new PhysicalKeyStroke(0x41, 0x1E, PhysicalKeyModifiers.Shift, false));

        new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("A", true);

        Assert.Single(sender.Batches);
        Assert.Equal(new ushort[] { 0x2A, 0x1E, 0x1E, 0x2A }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PreferPhysicalKeysUsesScanCodeForUnmodifiedLowercaseW()
    {
        var sender = new RecordingSender();
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('w', new PhysicalKeyStroke(0x57, 0x11, PhysicalKeyModifiers.None, false));

        new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("w", true);

        Assert.Single(sender.Batches);
        Assert.Equal(new ushort[] { 0x11, 0x11 }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PreferPhysicalKeysUsesOneAtomicBatchForShiftedPunctuation()
    {
        var sender = new RecordingSender();
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('!', new PhysicalKeyStroke(0x31, 0x02, PhysicalKeyModifiers.Shift, false));

        new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("!", true);

        Assert.Single(sender.Batches);
        Assert.Equal(new ushort[] { 0x2A, 0x02, 0x02, 0x2A }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PhysicalSendFailureCompensatesMainAndModifiersInReverseOrder()
    {
        var sender = new FaultingRecordingSender(throwOnCalls: [1]);
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('A', new PhysicalKeyStroke(0x41, 0x1E, PhysicalKeyModifiers.Shift, false));

        Assert.Throws<InvalidOperationException>(() => new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("A", true));

        Assert.Equal(2, sender.Batches.Count);
        Assert.Equal(new ushort[] { 0x2A, 0x1E, 0x1E, 0x2A }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x1E, 0x2A }, sender.Batches[1].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 10, 10 }, sender.Batches[1].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PhysicalSendFailureWithoutModifiersStillCompensatesMainKeyUp()
    {
        var sender = new FaultingRecordingSender(throwOnCalls: [1]);
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('w', new PhysicalKeyStroke(0x57, 0x11, PhysicalKeyModifiers.None, false));

        Assert.Throws<InvalidOperationException>(() => new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("w", true));

        Assert.Equal(2, sender.Batches.Count);
        Assert.Equal(new ushort[] { 0x11, 0x11 }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new ushort[] { 0x11 }, sender.Batches[1].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 10 }, sender.Batches[1].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PhysicalCompensationFailurePreservesOriginalException()
    {
        var original = new InvalidOperationException("original");
        var sender = new FaultingRecordingSender(throwOnCalls: [1, 2], exception: original);
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('A', new PhysicalKeyStroke(0x41, 0x1E, PhysicalKeyModifiers.Shift, false));

        var actual = Assert.Throws<InvalidOperationException>(() => new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("A", true));

        Assert.Same(original, actual);
        Assert.Equal(2, sender.Batches.Count);
    }

    [Fact]
    public void AltGrUsesRightAltExtendedAndCompensatesInReverseOrder()
    {
        var sender = new FaultingRecordingSender(throwOnCalls: [1]);
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('@', new PhysicalKeyStroke(0x51, 0x10, PhysicalKeyModifiers.Control | PhysicalKeyModifiers.Alt, false));

        Assert.Throws<InvalidOperationException>(() => new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("@", true));

        Assert.Equal(new ushort[] { 0x1D, 0x38, 0x10, 0x10, 0x38, 0x1D }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 9, 8, 10, 11, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x10, 0x38, 0x1D }, sender.Batches[1].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 10, 11, 10 }, sender.Batches[1].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void AltOnlyUsesLeftAltWithoutExtendedFlag()
    {
        var sender = new RecordingSender();
        var mapper = new RecordingPhysicalKeyMapper();
        mapper.Add('@', new PhysicalKeyStroke(0x32, 0x03, PhysicalKeyModifiers.Alt, false));

        new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("@", true);

        Assert.Single(sender.Batches);
        Assert.Equal(new ushort[] { 0x38, 0x03, 0x03, 0x38 }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void PreferPhysicalKeysFallsBackToUnicodeForUnmappedNonAscii()
    {
        var sender = new RecordingSender();
        var mapper = new RecordingPhysicalKeyMapper();

        new Win32InputInjector(sender, textInputPacer: new NoOpPacer(), physicalKeyMapper: mapper).InjectText("中", true);

        Assert.Single(sender.Batches);
        AssertUnicodeBatch(sender.Batches[0], '中');
    }

    [Fact]
    public void TextLineBreaksUseEnterAndNormalizeCrLf()
    {
        var sender = new RecordingSender();

        new Win32InputInjector(sender).InjectText("ABC\r\nXYZ");

        Assert.Equal(7, sender.Batches.Count);
        AssertUnicodeBatch(sender.Batches[0], 'A');
        AssertUnicodeBatch(sender.Batches[1], 'B');
        AssertUnicodeBatch(sender.Batches[2], 'C');
        AssertEnterBatch(sender.Batches[3]);
        AssertUnicodeBatch(sender.Batches[4], 'X');
        AssertUnicodeBatch(sender.Batches[5], 'Y');
        AssertUnicodeBatch(sender.Batches[6], 'Z');
    }

    [Fact]
    public void TextSendBatchesArePacedInOrderAfterEachSuccessfulSend()
    {
        var events = new List<string>();
        var sender = new OrderedRecordingSender(events);
        var pacer = new RecordingTextInputPacer(events);

        new Win32InputInjector(sender, pacer).InjectText("ABC\r\nXYZ");

        Assert.Equal(
        [
            "send:A", "pace",
            "send:B", "pace",
            "send:C", "pace",
            "send:Enter", "pace",
            "send:X", "pace",
            "send:Y", "pace",
            "send:Z", "pace"
        ], events);
    }

    [Fact]
    public void OneMillisecondTextInputPacerRequestsOneMillisecondSleep()
    {
        var sleeper = new RecordingThreadSleeper();

        new OneMillisecondTextInputPacer(sleeper).Pace();

        Assert.Equal([1], sleeper.RequestedMilliseconds);
    }

    [Fact]
    public void LoneLfAndCrEachUseOneEnterBatch()
    {
        var sender = new RecordingSender();

        new Win32InputInjector(sender).InjectText("A\nB\rC");

        Assert.Equal(5, sender.Batches.Count);
        AssertUnicodeBatch(sender.Batches[0], 'A');
        AssertEnterBatch(sender.Batches[1]);
        AssertUnicodeBatch(sender.Batches[2], 'B');
        AssertEnterBatch(sender.Batches[3]);
        AssertUnicodeBatch(sender.Batches[4], 'C');
    }

    [Fact]
    public void KeysMouseMoveButtonsAndWheelMapToSendInputFlags()
    {
        var sender = new RecordingSender();
        var injector = new Win32InputInjector(sender);
        foreach (var key in Enum.GetValues<ReceiverKey>()) injector.PressKey(key);
        injector.MovePointer(12, -8);
        injector.SetPointerButton(MouseButton.Left, ButtonAction.Down);
        injector.SetPointerButton(MouseButton.Left, ButtonAction.Up);
        injector.SetPointerButton(MouseButton.Right, ButtonAction.Down);
        injector.SetPointerButton(MouseButton.Right, ButtonAction.Up);
        injector.Wheel(-120);

        Assert.Equal(new ushort[] { 0x26, 0x28, 0x25, 0x27, 0x08, 0x0D, 0x1B }, sender.Batches.Take(7).Select(b => b[0].U.ki.wVk));
        Assert.Equal(0x0001u, sender.Batches[7][0].U.mi.dwFlags);
        Assert.Equal((12, -8), (sender.Batches[7][0].U.mi.dx, sender.Batches[7][0].U.mi.dy));
        Assert.Equal(new uint[] { 2, 4, 8, 16 }, sender.Batches.Skip(8).Take(4).Select(b => b[0].U.mi.dwFlags));
        Assert.Equal(0x0800u, sender.Batches[^1][0].U.mi.dwFlags);
        Assert.Equal(unchecked((uint)-120), sender.Batches[^1][0].U.mi.mouseData);
    }

    [Fact]
    public void GameKeyStateUsesFixedScanCodesAndDownUpFlags()
    {
        var sender = new RecordingSender();
        var injector = new Win32InputInjector(sender);

        injector.SetKeyState(GameKey.W, ButtonAction.Down);
        injector.SetKeyState(GameKey.A, ButtonAction.Up);
        injector.SetKeyState(GameKey.S, ButtonAction.Down);
        injector.SetKeyState(GameKey.D, ButtonAction.Up);

        Assert.Equal(new ushort[] { 0x11, 0x1E, 0x1F, 0x20 }, sender.Batches.Select(batch => batch[0].U.ki.wScan));
        Assert.Equal(new uint[] { 0x0008, 0x000A, 0x0008, 0x000A }, sender.Batches.Select(batch => batch[0].U.ki.dwFlags));
        Assert.All(sender.Batches, batch => Assert.Equal((ushort)0, batch[0].U.ki.wVk));
    }

    [Fact]
    public void SystemShortcutsUseOrderedScanCodeChords()
    {
        var sender = new RecordingSender();
        var injector = new Win32InputInjector(sender);

        injector.PressSystemShortcut(SystemShortcut.Shift);
        injector.PressSystemShortcut(SystemShortcut.ControlSpace);
        injector.PressSystemShortcut(SystemShortcut.CapsLock);
        injector.PressSystemShortcut(SystemShortcut.WindowsSpace);
        injector.PressSystemShortcut(SystemShortcut.ControlShift);
        injector.PressSystemShortcut(SystemShortcut.AltShift);

        Assert.Equal(new ushort[] { 0x2A, 0x2A }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 10 }, sender.Batches[0].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x1D, 0x39, 0x39, 0x1D }, sender.Batches[1].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[1].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x3A, 0x3A }, sender.Batches[2].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 10 }, sender.Batches[2].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x5B, 0x39, 0x39, 0x5B }, sender.Batches[3].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 9, 8, 10, 11 }, sender.Batches[3].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x1D, 0x2A, 0x2A, 0x1D }, sender.Batches[4].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[4].Select(x => x.U.ki.dwFlags));
        Assert.Equal(new ushort[] { 0x38, 0x2A, 0x2A, 0x38 }, sender.Batches[5].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 8, 8, 10, 10 }, sender.Batches[5].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void SystemShortcutFailureCompensatesReverseModifierReleases()
    {
        var sender = new FaultingRecordingSender(throwOnCalls: [1]);
        var injector = new Win32InputInjector(sender);

        Assert.Throws<InvalidOperationException>(() => injector.PressSystemShortcut(SystemShortcut.ControlSpace));

        Assert.Equal(2, sender.Batches.Count);
        Assert.Equal(new ushort[] { 0x1D, 0x39, 0x39, 0x1D }, sender.Batches[0].Select(x => x.U.ki.wScan));
        Assert.Equal(new ushort[] { 0x39, 0x1D }, sender.Batches[1].Select(x => x.U.ki.wScan));
        Assert.Equal(new uint[] { 10, 10 }, sender.Batches[1].Select(x => x.U.ki.dwFlags));
    }

    [Fact]
    public void RealSendInputBoundaryAcceptsUnicodeEvents()
    {
        // This is deliberately the only test that crosses the real user32 boundary.
        // U+0000 is used so the verification cannot alter visible text in another application.
        var error = Record.Exception(() => new Win32InputInjector().InjectText("\0"));
        Assert.Null(error);
    }

    private static void AssertUnicodeBatch(INPUT[] batch, char codeUnit)
    {
        Assert.Equal(2, batch.Length);
        Assert.Equal(new ushort[] { codeUnit, codeUnit }, batch.Select(input => input.U.ki.wScan));
        Assert.Equal(new uint[] { 4, 6 }, batch.Select(input => input.U.ki.dwFlags));
        Assert.All(batch, input => Assert.Equal((ushort)0, input.U.ki.wVk));
    }

    private static void AssertEnterBatch(INPUT[] batch)
    {
        Assert.Equal(2, batch.Length);
        Assert.All(batch, input => Assert.Equal((ushort)0x0D, input.U.ki.wVk));
        Assert.All(batch, input => Assert.Equal((ushort)0, input.U.ki.wScan));
        Assert.Equal(new uint[] { 0, 2 }, batch.Select(input => input.U.ki.dwFlags));
    }

    private sealed class RecordingSender : IWin32SendInput
    {
        public List<INPUT[]> Batches { get; } = [];
        public void Send(INPUT[] inputs) => Batches.Add(inputs);
    }

    private sealed class CancellingSender(CancellationTokenSource cancellation, int cancelAfterSends) : IWin32SendInput
    {
        public List<INPUT[]> Batches { get; } = [];
        public void Send(INPUT[] inputs)
        {
            Batches.Add(inputs);
            if (Batches.Count >= cancelAfterSends) cancellation.Cancel();
        }
    }

    private sealed class FaultingRecordingSender(IEnumerable<int> throwOnCalls, Exception? exception = null) : IWin32SendInput
    {
        private readonly HashSet<int> _throwOnCalls = throwOnCalls.ToHashSet();
        private readonly Exception _exception = exception ?? new InvalidOperationException("send failed");
        private int _callCount;
        public List<INPUT[]> Batches { get; } = [];
        public void Send(INPUT[] inputs)
        {
            Batches.Add(inputs);
            _callCount++;
            if (_throwOnCalls.Contains(_callCount)) throw _exception;
        }
    }

    private sealed class NoOpPacer : ITextInputPacer
    {
        public void Pace() { }
    }

    private sealed class RecordingPhysicalKeyMapper : IPhysicalKeyMapper
    {
        private readonly Dictionary<char, PhysicalKeyStroke> _mappings = [];
        public void Add(char character, PhysicalKeyStroke stroke) => _mappings[character] = stroke;
        public bool TryMap(char character, out PhysicalKeyStroke stroke) => _mappings.TryGetValue(character, out stroke);
    }

    private sealed class OrderedRecordingSender(List<string> events) : IWin32SendInput
    {
        public void Send(INPUT[] inputs)
        {
            events.Add(inputs[0].U.ki.wVk == 0x0D
                ? "send:Enter"
                : $"send:{(char)inputs[0].U.ki.wScan}");
        }
    }

    private sealed class RecordingTextInputPacer(List<string> events) : ITextInputPacer
    {
        public void Pace() => events.Add("pace");
    }

    private sealed class RecordingThreadSleeper : IThreadSleeper
    {
        public List<int> RequestedMilliseconds { get; } = [];
        public void Sleep(int milliseconds) => RequestedMilliseconds.Add(milliseconds);
    }
}
