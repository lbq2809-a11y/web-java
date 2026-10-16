# Script sinh cặp khóa RSA 2048-bit (PKCS#8) dùng chung cho JWT toàn hệ thống
# Sử dụng trực tiếp JDK 21 (KeyPairGenerator) nên chạy được trên mọi máy cài Java mà không cần cài thêm OpenSSL
# Cách chạy từ thư mục gốc dự án: .\keys\generate-keys.ps1

$ErrorActionPreference = "Stop"
$KeysDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TempJavaFile = Join-Path $KeysDir "KeyGenTemp.java"

$javaCode = @"
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public class KeyGenTemp {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        Base64.Encoder mimeEncoder = Base64.getMimeEncoder(64, "\n".getBytes());

        // getPrivate().getEncoded() trong Java chuẩn mặc định là PKCS#8
        String privatePem = "-----BEGIN PRIVATE KEY-----\n"
                + mimeEncoder.encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        // getPublic().getEncoded() trong Java chuẩn mặc định là X.509 SubjectPublicKeyInfo
        String publicPem = "-----BEGIN PUBLIC KEY-----\n"
                + mimeEncoder.encodeToString(kp.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";

        Files.writeString(dir.resolve("privateKey.pem"), privatePem);
        Files.writeString(dir.resolve("publicKey.pem"), publicPem);
    }
}
"@

try {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($TempJavaFile, $javaCode, $utf8NoBom)
    & java $TempJavaFile $KeysDir
    if ($LASTEXITCODE -ne 0) {
        throw "Khong the chay lenh java de sinh khoa RSA."
    }
    Write-Host "Da sinh thanh cong cap khoa RSA 2048-bit (PKCS#8):" -ForegroundColor Green
    Write-Host " - Private Key (da duoc .gitignore): $(Join-Path $KeysDir 'privateKey.pem')"
    Write-Host " - Public Key  (dung chung verify):  $(Join-Path $KeysDir 'publicKey.pem')"
} finally {
    if (Test-Path $TempJavaFile) {
        Remove-Item $TempJavaFile -Force
    }
}

