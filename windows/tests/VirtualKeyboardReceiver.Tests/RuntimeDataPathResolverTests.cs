using VirtualKeyboardReceiver.Core;

namespace VirtualKeyboardReceiver.Tests;

public sealed class RuntimeDataPathResolverTests
{
    [Fact]
    public void Resolve_WhenExeIsInsideRepository_UsesRepositoryRuntimeData()
    {
        using var directory = new TemporaryDirectory();
        var repository = Path.Combine(directory.Path, "repository");
        var baseDirectory = Path.Combine(repository, "artifacts", "windows-x64");
        Directory.CreateDirectory(Path.Combine(repository, "docs"));
        Directory.CreateDirectory(baseDirectory);
        File.WriteAllText(Path.Combine(repository, "docs", "SPEC.md"), "marker");

        var result = RuntimeDataPathResolver.Resolve(baseDirectory);

        Assert.Equal(Path.Combine(repository, "runtime-data"), result);
    }

    [Fact]
    public void Resolve_WhenNoRepositoryMarkerExists_UsesExeDirectoryRuntimeData()
    {
        using var directory = new TemporaryDirectory();
        var baseDirectory = Path.Combine(directory.Path, "ordinary-extraction");
        Directory.CreateDirectory(baseDirectory);

        var result = RuntimeDataPathResolver.Resolve(baseDirectory);

        Assert.Equal(Path.Combine(baseDirectory, "runtime-data"), result);
    }

    private sealed class TemporaryDirectory : IDisposable
    {
        public string Path { get; } = System.IO.Path.Combine(
            System.IO.Path.GetTempPath(),
            "virtual-keyboard-path-test-" + Guid.NewGuid().ToString("N"));

        public TemporaryDirectory() => Directory.CreateDirectory(Path);
        public void Dispose() => Directory.Delete(Path, true);
    }
}
