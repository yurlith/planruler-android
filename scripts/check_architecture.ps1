$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$forbidden = @(
    @{ Path = "feature"; Pattern = "com\.planruler\.(engine\.default|document\.android|project\.local)" },
    # Shipped feature code reaches the 3D engine only through its API. Test sources are exempt:
    # they compose the real engine the way the app's composition root does.
    @{ Path = "feature"; Pattern = "com\.planruler\.fabrication3d\.engine\."; Scope = "main" },
    @{ Path = "core\model"; Pattern = "^import android\." },
    @{ Path = "core\engine-api"; Pattern = "^import android\." },
    @{ Path = "core\fabrication3d-api"; Pattern = "^import android\." },
    @{ Path = "core\fabrication3d-engine"; Pattern = "^import android\." },
    # The engine stays independent of the pipe calculator; the catalog bridge owns that link.
    @{ Path = "core\fabrication3d-api"; Pattern = "com\.planruler\.pipecalculator" },
    @{ Path = "core\fabrication3d-engine\src\main"; Pattern = "com\.planruler\.pipecalculator" }
)
$failed = $false
foreach ($rule in $forbidden) {
    $target = Join-Path $root $rule.Path
    if (-not (Test-Path $target)) { continue }
    $violations = Get-ChildItem -Recurse -File $target -Filter *.kt |
        Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\bin\\" } |
        Where-Object { $rule.Scope -ne "main" -or $_.FullName -match "\\src\\main\\" } |
        Select-String -Pattern $rule.Pattern -CaseSensitive
    if ($violations) {
        $failed = $true
        $violations | ForEach-Object {
            Write-Output "$($_.Path):$($_.LineNumber): forbidden dependency $($_.Line.Trim())"
        }
    }
}
if ($failed) {
    Write-Error "Architecture dependency checks failed."
}
Write-Output "Architecture dependency checks passed."
