param(
    [string]$Profile = "leetduel",
    [string]$Namespace = "leetduel",
    [string]$SecretFile = "deploy/.env.k8s.local"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$chart = Join-Path $root "deploy/helm/leetduel"
$secretPath = Join-Path $root $SecretFile

function Invoke-Checked {
    param(
        [string]$Command,
        [string[]]$Arguments
    )

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Command failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path -LiteralPath $secretPath)) {
    throw "Missing $SecretFile. Copy deploy/.env.k8s.local.example, replace every value, and retry."
}

$secrets = @{}
foreach ($line in Get-Content -LiteralPath $secretPath) {
    $trimmed = $line.Trim()
    if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) {
        continue
    }
    $parts = $trimmed -split "=", 2
    if ($parts.Count -ne 2) {
        throw "Invalid secret line: $line"
    }
    $secrets[$parts[0].Trim()] = $parts[1].Trim()
}

if (-not $secrets.ContainsKey("NVIDIA_API_KEY")) {
    $siblingEnv = Join-Path (Split-Path $root -Parent) "Varad-BTP/deployment/api/.env"
    if (Test-Path -LiteralPath $siblingEnv) {
        foreach ($line in Get-Content -LiteralPath $siblingEnv) {
            $trimmed = $line.Trim()
            if ($trimmed -match '^NVIDIA_API_KEY=(.+)$') {
                $secrets["NVIDIA_API_KEY"] = $Matches[1].Trim()
                break
            }
        }
    }
}

$requiredKeys = @(
    "POSTGRES_PASSWORD",
    "POSTGRES_USER_PASSWORD",
    "REDIS_PASSWORD",
    "RABBITMQ_PASSWORD",
    "JWT_SECRET"
)
foreach ($key in $requiredKeys) {
    if (-not $secrets.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($secrets[$key])) {
        throw "Missing required key $key in $SecretFile"
    }
}

Invoke-Checked "minikube" @("profile", $Profile)
$namespaceManifest = & kubectl create namespace $Namespace --dry-run=client -o yaml
if ($LASTEXITCODE -ne 0) {
    throw "Could not render namespace manifest"
}
$namespaceManifest | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) {
    throw "Could not apply namespace manifest"
}

$secretArguments = @(
    "create", "secret", "generic", "leetduel-secrets",
    "--namespace", $Namespace,
    "--from-literal=postgres-password=$($secrets["POSTGRES_PASSWORD"])",
    "--from-literal=password=$($secrets["POSTGRES_USER_PASSWORD"])",
    "--from-literal=redis-password=$($secrets["REDIS_PASSWORD"])",
    "--from-literal=rabbitmq-password=$($secrets["RABBITMQ_PASSWORD"])",
    "--from-literal=jwt-secret=$($secrets["JWT_SECRET"])",
    "--dry-run=client", "-o", "yaml"
)
if ($secrets.ContainsKey("NVIDIA_API_KEY") -and -not [string]::IsNullOrWhiteSpace($secrets["NVIDIA_API_KEY"])) {
    $secretArguments = $secretArguments[0..10] +
        "--from-literal=nvidia-api-key=$($secrets["NVIDIA_API_KEY"])" +
        $secretArguments[11..13]
}
$secretManifest = & kubectl @secretArguments
if ($LASTEXITCODE -ne 0) {
    throw "Could not render LeetDuel Secret"
}
$secretManifest | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) {
    throw "Could not apply LeetDuel Secret"
}

$ingressHost = "leetduel.$(minikube ip --profile $Profile).nip.io"
if ($LASTEXITCODE -ne 0) {
    throw "Could not determine Minikube IP"
}

Invoke-Checked "helm" @("dependency", "build", $chart)
Invoke-Checked "helm" @(
    "upgrade", "--install", "leetduel", $chart,
    "--namespace", $Namespace,
    "--create-namespace",
    "--set", "global.ingress.host=$ingressHost",
    "--wait",
    "--timeout", "10m"
)

Write-Host "LeetDuel is deployed at http://$ingressHost"
