namespace VirtualKeyboardReceiver.Core;

public static class RuntimeDataPathResolver
{
    public static string Resolve(string baseDirectory)
    {
        var normalizedBase = Path.GetFullPath(baseDirectory);
        var directory = new DirectoryInfo(normalizedBase);
        while (directory is not null)
        {
            if (File.Exists(Path.Combine(directory.FullName, "docs", "SPEC.md")))
                return Path.Combine(directory.FullName, "runtime-data");
            directory = directory.Parent;
        }

        return Path.Combine(normalizedBase, "runtime-data");
    }
}
