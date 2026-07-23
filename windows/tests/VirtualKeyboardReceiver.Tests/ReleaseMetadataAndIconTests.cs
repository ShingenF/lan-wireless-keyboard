using System.Text;

namespace VirtualKeyboardReceiver.Tests;

public sealed class ReleaseMetadataAndIconTests
{
    [Fact]
    public void WindowsProjectUsesV13ReleaseMetadata()
    {
        var project = ReadRepoFile("src/VirtualKeyboardReceiver/VirtualKeyboardReceiver.csproj");

        Assert.Contains("<Version>1.3.0</Version>", project);
        Assert.Contains("<AssemblyVersion>1.3.0.0</AssemblyVersion>", project);
        Assert.Contains("<FileVersion>1.3.0.0</FileVersion>", project);
        Assert.Contains("<InformationalVersion>1.3.0</InformationalVersion>", project);
        Assert.Contains("version=\"1.3.0.0\"", ReadRepoFile("src/VirtualKeyboardReceiver/app.manifest"));
    }

    [Fact]
    public void ReleaseProjectsRemoveLocalDebugPaths()
    {
        var appProject = ReadRepoFile("src/VirtualKeyboardReceiver/VirtualKeyboardReceiver.csproj");
        var coreProject = ReadRepoFile("src/VirtualKeyboardReceiver.Core/VirtualKeyboardReceiver.Core.csproj");

        foreach (var project in new[] { appProject, coreProject })
        {
            Assert.Contains("<ContinuousIntegrationBuild>true</ContinuousIntegrationBuild>", project);
            Assert.Contains("<PathMap>$(MSBuildProjectDirectory)=/_/src/", project);
            Assert.Contains("<DebugType>None</DebugType>", project);
            Assert.Contains("<DebugSymbols>false</DebugSymbols>", project);
        }
    }

    [Fact]
    public void ApplicationAndTrayUseTheSameHugeiconsSource()
    {
        var generator = ReadRepoFile("tools/generate-icons.py");
        var assetReadme = ReadRepoFile("src/VirtualKeyboardReceiver/Assets/README.md");
        var svg = ReadRepoFile("src/VirtualKeyboardReceiver/Assets/hugeicons-keyboard-stroke-rounded.svg");

        Assert.Contains("APPLICATION_SOURCE = ASSETS / \"hugeicons-keyboard-stroke-rounded.svg\"", generator);
        Assert.Contains("TRAY_SOURCE = ASSETS / \"hugeicons-keyboard-stroke-rounded.svg\"", generator);
        Assert.Contains("application-black", generator);
        Assert.Contains("#000000", generator);
        Assert.Contains("#FFFFFF", generator);
        Assert.Contains("--default-background-color=00000000", generator);
        Assert.Contains("KeyboardIcon", assetReadme);
        Assert.DoesNotContain("material", assetReadme, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("phonelink", assetReadme, StringComparison.OrdinalIgnoreCase);
        Assert.Equal(6, svg.Split("<path", StringSplitOptions.RemoveEmptyEntries).Length - 1);
        Assert.Contains("currentColor", svg);
    }

    [Theory]
    [InlineData("receiver.ico")]
    [InlineData("tray-hugeicons-keyboard-black.ico")]
    [InlineData("tray-hugeicons-keyboard-white.ico")]
    public void GeneratedIconsContainAllTransparentRgbaSizes(string fileName)
    {
        var bytes = File.ReadAllBytes(Path.Combine(RepoRoot(), "src/VirtualKeyboardReceiver/Assets", fileName));
        var expected = new[] { 16, 20, 24, 32, 40, 48, 64, 256 };

        Assert.Equal((byte)0, bytes[0]);
        Assert.Equal((byte)1, bytes[2]);
        Assert.Equal((byte)expected.Length, bytes[4]);
        for (var index = 0; index < expected.Length; index++)
        {
            var entry = 6 + index * 16;
            var size = bytes[entry] == 0 ? 256 : bytes[entry];
            Assert.Equal(expected[index], size);
            Assert.Equal((byte)32, bytes[entry + 6]);
            var imageOffset = BitConverter.ToInt32(bytes, entry + 12);
            Assert.Equal(new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }, bytes[imageOffset..(imageOffset + 8)]);
            Assert.Equal((byte)6, bytes[imageOffset + 25]); // PNG color type 6 = RGBA.
        }
    }

    [Theory]
    [InlineData("receiver.ico", 0)]
    [InlineData("tray-hugeicons-keyboard-black.ico", 0)]
    [InlineData("tray-hugeicons-keyboard-white.ico", 255)]
    public void GeneratedIconsUseExpectedOpaquePixelColor(string fileName, byte expectedColor)
    {
        var bytes = File.ReadAllBytes(Path.Combine(RepoRoot(), "src/VirtualKeyboardReceiver/Assets", fileName));
        var pngOffset = BitConverter.ToInt32(bytes, 6 + 12);
        var pngLength = BitConverter.ToInt32(bytes, 6 + 8);
        var colors = DecodeOpaqueRgb(bytes[pngOffset..(pngOffset + pngLength)]);

        Assert.NotEmpty(colors);
        Assert.All(colors, color =>
        {
            Assert.Equal(expectedColor, (byte)(color >> 16));
            Assert.Equal(expectedColor, (byte)(color >> 8));
            Assert.Equal(expectedColor, (byte)color);
        });
    }

    [Fact]
    public void MaterialIconSourceAndAttributionAreRemoved()
    {
        var root = RepoRoot();

        Assert.False(File.Exists(Path.Combine(root, "src/VirtualKeyboardReceiver/Assets/material-symbols-phonelink-outlined.svg")));
        Assert.DoesNotContain("material", ReadRepoFile("README.md"), StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("phonelink", ReadRepoFile("README.md"), StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("material", ReadRepoFile("THIRD_PARTY_NOTICES.md"), StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("phonelink", ReadRepoFile("THIRD_PARTY_NOTICES.md"), StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("material", ReadRepoFile("docs/REVIEW.md"), StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("phonelink", ReadRepoFile("docs/REVIEW.md"), StringComparison.OrdinalIgnoreCase);
    }

    private static string ReadRepoFile(string relativePath) =>
        File.ReadAllText(Path.Combine(RepoRoot(), relativePath.Replace('/', Path.DirectorySeparatorChar)), Encoding.UTF8);

    private static HashSet<uint> DecodeOpaqueRgb(byte[] png)
    {
        var width = ReadBigEndianInt32(png, 16);
        var height = ReadBigEndianInt32(png, 20);
        var compressed = new List<byte>();
        var offset = 8;
        while (offset < png.Length)
        {
            var length = ReadBigEndianInt32(png, offset);
            var type = Encoding.ASCII.GetString(png, offset + 4, 4);
            if (type == "IDAT") compressed.AddRange(png[(offset + 8)..(offset + 8 + length)]);
            offset += 12 + length;
        }

        using var input = new MemoryStream(compressed.ToArray());
        using var zlib = new System.IO.Compression.ZLibStream(input, System.IO.Compression.CompressionMode.Decompress);
        using var decoded = new MemoryStream();
        zlib.CopyTo(decoded);
        var raw = decoded.ToArray();
        var stride = width * 4;
        var previous = new byte[stride];
        var colors = new HashSet<uint>();
        for (var y = 0; y < height; y++)
        {
            var rowOffset = y * (stride + 1);
            var filter = raw[rowOffset];
            var row = raw[(rowOffset + 1)..(rowOffset + 1 + stride)].ToArray();
            for (var i = 0; i < row.Length; i++)
            {
                var left = i >= 4 ? row[i - 4] : (byte)0;
                var up = previous[i];
                var upLeft = i >= 4 ? previous[i - 4] : (byte)0;
                row[i] = filter switch
                {
                    0 => row[i],
                    1 => (byte)(row[i] + left),
                    2 => (byte)(row[i] + up),
                    3 => (byte)(row[i] + ((left + up) / 2)),
                    4 => (byte)(row[i] + Paeth(left, up, upLeft)),
                    _ => throw new InvalidDataException($"Unsupported PNG filter {filter}")
                };
            }

            for (var x = 0; x < width; x++)
            {
                var pixel = x * 4;
                if (row[pixel + 3] != 0)
                    colors.Add((uint)(row[pixel] << 16 | row[pixel + 1] << 8 | row[pixel + 2]));
            }
            previous = row;
        }

        return colors;
    }

    private static byte Paeth(byte left, byte up, byte upLeft)
    {
        var estimate = left + up - upLeft;
        var leftDistance = Math.Abs(estimate - left);
        var upDistance = Math.Abs(estimate - up);
        var upLeftDistance = Math.Abs(estimate - upLeft);
        return leftDistance <= upDistance && leftDistance <= upLeftDistance ? left : upDistance <= upLeftDistance ? up : upLeft;
    }

    private static int ReadBigEndianInt32(byte[] bytes, int offset) =>
        (bytes[offset] << 24) | (bytes[offset + 1] << 16) | (bytes[offset + 2] << 8) | bytes[offset + 3];

    private static string RepoRoot()
    {
        for (var directory = new DirectoryInfo(AppContext.BaseDirectory); directory is not null; directory = directory.Parent)
        {
            if (File.Exists(Path.Combine(directory.FullName, "docs", "SPEC.md"))) return directory.FullName;
        }

        throw new InvalidOperationException("Repository root not found");
    }
}
