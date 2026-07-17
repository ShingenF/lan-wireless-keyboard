using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace VirtualKeyboardReceiver.Core;

public sealed class ReceiverIdentity
{
    private const string Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public required X509Certificate2 Certificate { get; init; }
    public required string PairingCode { get; init; }
    public string GroupedPairingCode => string.Join('-', Enumerable.Range(0, 4).Select(i => PairingCode.Substring(i * 4, 4)));
    public string Fingerprint => Convert.ToHexString(SHA256.HashData(Certificate.Export(X509ContentType.Cert)));

    public static ReceiverIdentity LoadOrCreate(string directory)
    {
        Directory.CreateDirectory(directory);
        var certPath = Path.Combine(directory, "receiver.pfx");
        var keyPath = Path.Combine(directory, "pairing-key.txt");
        if (!File.Exists(certPath) || !File.Exists(keyPath))
        {
            using var rsa = RSA.Create(2048);
            var request = new CertificateRequest("CN=Virtual Keyboard Receiver", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
            request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
            request.CertificateExtensions.Add(new X509SubjectKeyIdentifierExtension(request.PublicKey, false));
            using var generated = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(10));
            File.WriteAllBytes(certPath, generated.Export(X509ContentType.Pfx));
            var random = RandomNumberGenerator.GetBytes(10);
            var chars = new char[16];
            ulong bits = 0; var bitCount = 0; var source = 0;
            for (var i = 0; i < chars.Length; i++)
            {
                while (bitCount < 5) { bits = (bits << 8) | random[source++]; bitCount += 8; }
                bitCount -= 5; chars[i] = Alphabet[(int)((bits >> bitCount) & 31)];
            }
            File.WriteAllText(keyPath, new string(chars));
        }
        var key = File.ReadAllText(keyPath).Trim().ToUpperInvariant();
        if (key.Length != 16 || key.Any(c => !Alphabet.Contains(c))) throw new InvalidDataException("Persisted pairing key is invalid.");
        return new ReceiverIdentity { Certificate = X509CertificateLoader.LoadPkcs12FromFile(certPath, null), PairingCode = key };
    }
}

