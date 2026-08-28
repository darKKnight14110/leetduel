param(
    [string]$Profile = "leetduel"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Checked {
    param(
        [string[]]$Arguments
    )

    & minikube @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "minikube image build failed with exit code $LASTEXITCODE"
    }
}

$components = @(
    @{ Tag = "leetduel-auth:dev"; Context = "services/auth-service" },
    @{ Tag = "leetduel-user:dev"; Context = "services/user-service" },
    @{ Tag = "leetduel-gateway:dev"; Context = "services/gateway" },
    @{ Tag = "leetduel-problem:dev"; Context = "services/problem-service" },
    @{ Tag = "leetduel-submission:dev"; Context = "services/submission-service" },
    @{ Tag = "leetduel-matchmaking:dev"; Context = "services/matchmaking-service" },
    @{ Tag = "leetduel-duel:dev"; Context = "services/duel-service" },
    @{ Tag = "leetduel-ws-gateway:dev"; Context = "services/ws-gateway" },
    @{ Tag = "leetduel-leaderboard:dev"; Context = "services/leaderboard-service" },
    @{ Tag = "leetduel-judge-dispatcher:dev"; Context = "services/judge-worker" },
    @{ Tag = "leetduel-frontend:dev"; Context = "frontend" }
)

foreach ($component in $components) {
    $contextPath = Join-Path $root $component.Context
    Invoke-Checked @(
        "image", "build", "--profile", $Profile,
        "--tag", $component.Tag,
        $contextPath
    )
}

$judgeContext = Join-Path $root "services/judge-worker"
Invoke-Checked @(
    "image", "build", "--profile", $Profile,
    "--file", (Join-Path $judgeContext "Dockerfile.executor"),
    "--tag", "leetduel-judge-executor:dev",
    $judgeContext
)

Write-Host "Built all LeetDuel images into Minikube profile '$Profile'."
