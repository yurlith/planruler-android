param(
    [string]$Alias = "planruler-upload"
)

$ErrorActionPreference = "Stop"

$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$signingDirectory = Join-Path $workspace "release-signing"
$keystorePath = Join-Path $signingDirectory "planruler-upload.jks"
$propertiesPath = Join-Path $workspace "keystore.properties"
$temporaryKeystore = Join-Path ([IO.Path]::GetTempPath()) ("planruler-upload-{0}.jks" -f [Guid]::NewGuid().ToString("N"))

if (Test-Path -LiteralPath $keystorePath) {
    throw "Upload keystore already exists: $keystorePath"
}
if (Test-Path -LiteralPath $propertiesPath) {
    throw "Signing properties already exist: $propertiesPath"
}

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$random = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
$password = [Convert]::ToBase64String($random).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$keytool = (Get-Command keytool -ErrorAction Stop).Source

try {
    $keytoolArguments = @(
        "-genkeypair",
        "-keystore", $temporaryKeystore,
        "-storetype", "JKS",
        "-storepass", $password,
        "-keypass", $password,
        "-alias", $Alias,
        "-keyalg", "RSA",
        "-keysize", "4096",
        "-validity", "10000",
        "-dname", "CN=PlanRuler Upload, OU=Android, O=VeraqisTech",
        "-noprompt"
    )
    & $keytool @keytoolArguments
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
    Move-Item -LiteralPath $temporaryKeystore -Destination $keystorePath

    $properties = @(
        "storeFile=release-signing/planruler-upload.jks"
        "storePassword=$password"
        "keyAlias=$Alias"
        "keyPassword=$password"
    )
    [IO.File]::WriteAllLines($propertiesPath, $properties, [Text.UTF8Encoding]::new($false))
} catch {
    if (Test-Path -LiteralPath $temporaryKeystore) {
        Remove-Item -LiteralPath $temporaryKeystore -Force
    }
    if (Test-Path -LiteralPath $keystorePath) {
        Remove-Item -LiteralPath $keystorePath -Force
    }
    throw
}

Write-Output "Created a new Play upload key. Back up release-signing/planruler-upload.jks and keystore.properties together."
