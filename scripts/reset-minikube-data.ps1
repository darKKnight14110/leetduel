param(
    [string]$Namespace = "leetduel",
    [switch]$Force
)

$ErrorActionPreference = "Stop"

if (-not $Force) {
    $confirmation = Read-Host "Type RESET to delete all LeetDuel demo data in namespace '$Namespace'"
    if ($confirmation -ne "RESET") {
        throw "Reset cancelled"
    }
}

& kubectl delete namespace $Namespace --ignore-not-found=true
if ($LASTEXITCODE -ne 0) {
    throw "Could not delete namespace $Namespace"
}

Write-Host "Deleted namespace '$Namespace' and its PVCs. Run deploy-minikube.ps1 to recreate the demo."
