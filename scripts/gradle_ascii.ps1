<#
.SYNOPSIS
Runs Gradle from an ASCII path.

.DESCRIPTION
This repository can sit under a path containing non-ASCII characters while the system
ANSI code page cannot represent them. Every JVM child process the Gradle daemon starts
receives its class path through the command line, which is encoded with that code page,
so the path arrives mangled and the worker fails to load a single class.

The script maps the project onto a temporary drive letter whose path is pure ASCII and
runs the wrapper from there, then releases the mapping. When the project path is already
ASCII it simply runs the wrapper in place.

.EXAMPLE
.\scripts\gradle_ascii.ps1 check
.\scripts\gradle_ascii.ps1 :app:connectedDebugAndroidTest --console=plain
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Test-AsciiPath([string]$path) {
    return -not ($path.ToCharArray() | Where-Object { [int]$_ -gt 127 })
}

if (Test-AsciiPath $root) {
    & (Join-Path $root "gradlew.bat") -p $root @GradleArgs
    exit $LASTEXITCODE
}

$link = Join-Path ([System.IO.Path]::GetTempPath()) "planruler-ascii-build"
$drive = $null
try {
    if (Test-Path $link) {
        Remove-Item $link -Force -Recurse -ErrorAction SilentlyContinue
    }
    New-Item -ItemType Junction -Path $link -Target $root | Out-Null

    $used = (Get-PSDrive -PSProvider FileSystem).Name
    foreach ($candidate in [char[]]"PQRSTUVWXYZ") {
        if ($used -notcontains [string]$candidate) { $drive = "${candidate}:"; break }
    }
    if (-not $drive) { throw "No free drive letter is available for the ASCII build mapping." }

    & cmd.exe /c "subst $drive `"$link`"" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "subst $drive failed." }

    # The drive letter only exists for this run, so a watched virtual file system would carry
    # stale roots into the next one ("Already watching path").
    & "$drive\gradlew.bat" -p "$drive\" --no-watch-fs @GradleArgs
    $exitCode = $LASTEXITCODE
} finally {
    if ($drive) { & cmd.exe /c "subst $drive /d" | Out-Null }
    if (Test-Path $link) { Remove-Item $link -Force -Recurse -ErrorAction SilentlyContinue }
}

exit $exitCode
