param(
    [string]$PracticeServiceUrl = "http://localhost:8092",
    [int]$BatchSize = 25
)

$ErrorActionPreference = "Stop"
do {
    $embedded = Invoke-RestMethod -Method Post -Uri "$PracticeServiceUrl/internal/practice/embeddings/backfill?batchSize=$([Math]::Min([Math]::Max($BatchSize, 1), 100))"
    Write-Host "Embedded $embedded documents in this batch."
} while ([int]$embedded -gt 0)
