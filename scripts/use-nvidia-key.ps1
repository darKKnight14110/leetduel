param(
    [string]$TargetFile = "deploy/.env.runtime"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sourceFile = Join-Path (Split-Path $root -Parent) "Varad-BTP/deployment/api/.env"
if (-not (Test-Path -LiteralPath $sourceFile)) { throw "Sibling NVIDIA environment file was not found." }
$line = Get-Content -LiteralPath $sourceFile | Where-Object { $_ -match '^NVIDIA_API_KEY=(.+)$' } | Select-Object -First 1
if ($null -eq $line) { throw "NVIDIA_API_KEY was not found in the sibling environment file." }
$keyValue = ($line -split "=", 2)[1].Trim()
$targetPath = Join-Path $root $TargetFile
New-Item -ItemType Directory -Force -Path (Split-Path $targetPath -Parent) | Out-Null
"NVIDIA_API_KEY=$keyValue" | Set-Content -Encoding UTF8 -Path $targetPath
Write-Host "Wrote the ignored runtime NVIDIA environment file without displaying the key."
