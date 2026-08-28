[CmdletBinding()]
param(
    [int]$DbServiceReplicas = 1,
    [int]$DbServiceCount = 7,
    [int]$DbPoolMax = 8,
    [int]$ReservedDbConnections = 12,
    [int]$PostgresMaxConnections = 100,
    [double]$AverageDbHoldMs = 25,
    [int]$JudgeSlots = 4,
    [double]$AverageJudgeSeconds = 8,
    [double]$SubmissionArrivalRate = 0.25,
    [int]$BurstSubmissions = 100,
    [int]$GatewayReplicas = 3,
    [double]$GatewayRequestsPerSecondPerReplica = 75,
    [int]$WsReplicas = 3,
    [int]$WsSessionsPerReplica = 1000
)

$positiveInputs = @{
    DbServiceReplicas = $DbServiceReplicas
    DbServiceCount = $DbServiceCount
    DbPoolMax = $DbPoolMax
    ReservedDbConnections = $ReservedDbConnections
    PostgresMaxConnections = $PostgresMaxConnections
    AverageDbHoldMs = $AverageDbHoldMs
    JudgeSlots = $JudgeSlots
    AverageJudgeSeconds = $AverageJudgeSeconds
    SubmissionArrivalRate = $SubmissionArrivalRate
    BurstSubmissions = $BurstSubmissions
    GatewayReplicas = $GatewayReplicas
    GatewayRequestsPerSecondPerReplica = $GatewayRequestsPerSecondPerReplica
    WsReplicas = $WsReplicas
    WsSessionsPerReplica = $WsSessionsPerReplica
}

foreach ($entry in $positiveInputs.GetEnumerator()) {
    if ($entry.Value -le 0) {
        throw "$($entry.Key) must be greater than zero."
    }
}

$pooledConnections = $DbServiceReplicas * $DbServiceCount * $DbPoolMax
$connectionHeadroom = $PostgresMaxConnections - $pooledConnections - $ReservedDbConnections
$theoreticalDbTransactionsPerSecond = $pooledConnections / ($AverageDbHoldMs / 1000)
$judgeThroughputPerSecond = $JudgeSlots / $AverageJudgeSeconds
$judgeThroughputPerMinute = $judgeThroughputPerSecond * 60
$judgeUtilization = $SubmissionArrivalRate / $judgeThroughputPerSecond
$spareJudgeCapacity = $judgeThroughputPerSecond - $SubmissionArrivalRate
$burstDrainSeconds = if ($spareJudgeCapacity -gt 0) {
    $BurstSubmissions / $spareJudgeCapacity
} else {
    [double]::PositiveInfinity
}
$gatewayRequestsPerSecond = $GatewayReplicas * $GatewayRequestsPerSecondPerReplica
$wsSessions = $WsReplicas * $WsSessionsPerReplica

Write-Host "LeetDuel capacity planning model"
Write-Host "Assumptions are planning values, not benchmark measurements."
Write-Host ""

$metrics = @(
[PSCustomObject]@{
    Metric = 'PostgreSQL pooled connections'
    Value = [math]::Round($pooledConnections, 2)
    Unit = 'connections'
    Formula = "$DbServiceCount services x $DbServiceReplicas replicas x $DbPoolMax max pool"
}
[PSCustomObject]@{
    Metric = 'PostgreSQL connection headroom'
    Value = [math]::Round($connectionHeadroom, 2)
    Unit = 'connections'
    Formula = "$PostgresMaxConnections max - $pooledConnections pooled - $ReservedDbConnections reserve"
}
[PSCustomObject]@{
    Metric = 'Theoretical DB transaction bound'
    Value = [math]::Round($theoreticalDbTransactionsPerSecond, 2)
    Unit = 'transactions/sec'
    Formula = "$pooledConnections connections / $AverageDbHoldMs ms average hold"
}
[PSCustomObject]@{
    Metric = 'Judge throughput'
    Value = [math]::Round($judgeThroughputPerMinute, 2)
    Unit = 'submissions/min'
    Formula = "$JudgeSlots slots / $AverageJudgeSeconds sec average execution"
}
[PSCustomObject]@{
    Metric = 'Judge modeled utilization'
    Value = "$(('{0:P0}' -f $judgeUtilization))"
    Unit = 'of executor capacity'
    Formula = "$SubmissionArrivalRate arrivals/sec / $([math]::Round($judgeThroughputPerSecond, 3)) capacity/sec"
}
[PSCustomObject]@{
    Metric = 'Burst drain estimate'
    Value = if ([double]::IsPositiveInfinity($burstDrainSeconds)) { 'unbounded' } else { [math]::Round($burstDrainSeconds, 2) }
    Unit = 'seconds after burst'
    Formula = "$BurstSubmissions submissions / $([math]::Round($spareJudgeCapacity, 3)) spare submissions/sec"
}
[PSCustomObject]@{
    Metric = 'Gateway planning throughput'
    Value = [math]::Round($gatewayRequestsPerSecond, 2)
    Unit = 'requests/sec'
    Formula = "$GatewayReplicas replicas x $GatewayRequestsPerSecondPerReplica requests/sec"
}
[PSCustomObject]@{
    Metric = 'WebSocket planning sessions'
    Value = [math]::Round($wsSessions, 2)
    Unit = 'concurrent sessions'
    Formula = "$WsReplicas replicas x $WsSessionsPerReplica sessions"
}
)
$metrics | Format-Table -AutoSize

if ($connectionHeadroom -lt 0) {
    Write-Error "The configured pool budget exceeds the PostgreSQL planning limit. Lower DB_POOL_MAX_SIZE or reduce replicas."
    exit 2
}

if ($judgeUtilization -ge 1) {
    Write-Warning "Judge arrivals meet or exceed modeled capacity; backlog will grow without more slots or faster execution."
}
