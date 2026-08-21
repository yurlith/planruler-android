param(
    [Parameter(Mandatory = $true)][string]$Pdf,
    [Parameter(Mandatory = $true)][string]$Csv,
    [Parameter(Mandatory = $true)][string]$Json
)

$ErrorActionPreference = 'Stop'

foreach ($path in @($Pdf, $Csv, $Json)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Export does not exist: $path"
    }
}

$pdfInfoPath = if ($env:PLANRULER_PDFINFO) {
    $env:PLANRULER_PDFINFO
} else {
    (Get-Command pdfinfo -ErrorAction Stop).Source
}
$pdfOutput = & $pdfInfoPath $Pdf 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "pdfinfo rejected the PDF: $($pdfOutput -join [Environment]::NewLine)"
}
$pagesLine = $pdfOutput | Where-Object { $_ -match '^Pages:\s+\d+' } | Select-Object -First 1
$sizeLine = $pdfOutput | Where-Object { $_ -match '^Page size:\s+' } | Select-Object -First 1
if (-not $pagesLine -or -not $sizeLine) {
    throw 'pdfinfo did not report page count and page size'
}

$csvText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Csv), [System.Text.Encoding]::UTF8)
$csvRows = @($csvText | ConvertFrom-Csv)
if ($csvText -notmatch 'type' -or $csvText -notmatch 'page') {
    throw 'CSV does not contain the required type/page headers'
}
if ($csvRows.Count -lt 1) {
    throw 'CSV contains no measurement rows'
}

$jsonText = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Json), [System.Text.Encoding]::UTF8)
$jsonObject = $jsonText | ConvertFrom-Json
if (-not $jsonObject.schemaVersion -or $jsonObject.schemaVersion -lt 1) {
    throw 'JSON schemaVersion is missing or invalid'
}
if (-not $jsonObject.id -or -not $jsonObject.pages) {
    throw 'JSON project identity/pages are missing'
}

Write-Output "PDF_EXTERNAL_VALIDATION=PASS $pagesLine $sizeLine"
Write-Output "CSV_EXTERNAL_VALIDATION=PASS rows=$($csvRows.Count)"
Write-Output "JSON_EXTERNAL_VALIDATION=PASS schemaVersion=$($jsonObject.schemaVersion)"
