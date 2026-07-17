using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class AdaptiveTrayIconTests
{
    [Theory]
    [InlineData(1, TrayIconTheme.Black)]
    [InlineData(0, TrayIconTheme.White)]
    [InlineData(null, TrayIconTheme.Black)]
    public void Start_MapsSystemThemeToExpectedIcon(int? systemUsesLightTheme, TrayIconTheme expected)
    {
        var source = new RecordingThemeSource { SystemUsesLightTheme = systemUsesLightTheme };
        var factory = new RecordingIconFactory();
        var surface = new RecordingTraySurface();
        var dispatcher = new QueueingDispatcher();
        using var lifetime = new AdaptiveTrayIconLifetime(source, factory, surface, dispatcher);

        lifetime.Start();
        dispatcher.RunAll();

        Assert.Equal([expected], factory.CreatedThemes);
        Assert.Single(surface.AssignedIcons);
    }

    [Fact]
    public void RepeatedThemeNotifications_OnlyReplaceWhenResolvedThemeChanges()
    {
        var source = new RecordingThemeSource { SystemUsesLightTheme = 1 };
        var factory = new RecordingIconFactory();
        var surface = new RecordingTraySurface();
        var dispatcher = new QueueingDispatcher();
        using var lifetime = new AdaptiveTrayIconLifetime(source, factory, surface, dispatcher);
        lifetime.Start();
        dispatcher.RunAll();

        source.RaiseChanged();
        source.RaiseChanged();
        dispatcher.RunAll();

        Assert.Equal([TrayIconTheme.Black], factory.CreatedThemes);
        Assert.Equal(0, factory.Icons[0].DisposeCount);

        source.SystemUsesLightTheme = 0;
        source.RaiseChanged();
        dispatcher.RunAll();
        source.RaiseChanged();
        dispatcher.RunAll();

        Assert.Equal([TrayIconTheme.Black, TrayIconTheme.White], factory.CreatedThemes);
        Assert.Equal(1, factory.Icons[0].DisposeCount);
        Assert.Equal(0, factory.Icons[1].DisposeCount);
        Assert.Equal(2, surface.AssignedIcons.Count);
    }

    [Fact]
    public void QueuedThemeCallbackAfterExit_DoesNotRecreateAndDisposesResourcesExactlyOnce()
    {
        var source = new RecordingThemeSource { SystemUsesLightTheme = 1 };
        var factory = new RecordingIconFactory();
        var surface = new RecordingTraySurface();
        var dispatcher = new QueueingDispatcher();
        var lifetime = new AdaptiveTrayIconLifetime(source, factory, surface, dispatcher);
        lifetime.Start();
        dispatcher.RunAll();

        source.SystemUsesLightTheme = 0;
        source.RaiseChanged();
        lifetime.Dispose();
        lifetime.Dispose();
        dispatcher.RunAll();
        source.RaiseChanged();
        dispatcher.RunAll();

        Assert.Equal([TrayIconTheme.Black], factory.CreatedThemes);
        Assert.Equal(1, factory.Icons[0].DisposeCount);
        Assert.Equal(1, source.DisposeCount);
        Assert.Equal(1, surface.DisposeCount);
        Assert.Single(surface.AssignedIcons);
    }

    private sealed class RecordingThemeSource : ISystemThemeSource
    {
        public event EventHandler? ThemeChanged;
        public int? SystemUsesLightTheme { get; set; }
        public int DisposeCount { get; private set; }
        public void RaiseChanged() => ThemeChanged?.Invoke(this, EventArgs.Empty);
        public void Dispose() => DisposeCount++;
    }

    private sealed class RecordingIconFactory : ITrayIconFactory
    {
        public List<TrayIconTheme> CreatedThemes { get; } = [];
        public List<RecordingIcon> Icons { get; } = [];
        public ITrayIconResource Create(TrayIconTheme theme)
        {
            CreatedThemes.Add(theme);
            var icon = new RecordingIcon();
            Icons.Add(icon);
            return icon;
        }
    }

    private sealed class RecordingIcon : ITrayIconResource
    {
        public int DisposeCount { get; private set; }
        public void Dispose() => DisposeCount++;
    }

    private sealed class RecordingTraySurface : ITrayIconSurface
    {
        public List<ITrayIconResource> AssignedIcons { get; } = [];
        public int DisposeCount { get; private set; }
        public void SetIcon(ITrayIconResource icon) => AssignedIcons.Add(icon);
        public void Dispose() => DisposeCount++;
    }

    private sealed class QueueingDispatcher : IUiDispatcher
    {
        private readonly Queue<Action> _actions = new();
        public void Post(Action action) => _actions.Enqueue(action);
        public void RunAll()
        {
            while (_actions.TryDequeue(out var action)) action();
        }
    }
}
