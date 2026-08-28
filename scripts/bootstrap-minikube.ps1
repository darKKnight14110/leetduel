param(
    [string]$Profile = "leetduel"
)

$ErrorActionPreference = "Stop"

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

Invoke-Checked "minikube" @(
    "start",
    "--profile", $Profile,
    "--driver=docker",
    "--cni=calico",
    "--cpus=6",
    "--memory=10240",
    "--disk-size=30g"
)
Invoke-Checked "minikube" @("profile", $Profile)
Invoke-Checked "minikube" @("addons", "enable", "ingress", "--profile", $Profile)
Invoke-Checked "minikube" @("addons", "enable", "metrics-server", "--profile", $Profile)

Write-Host "Minikube profile '$Profile' is ready with ingress and metrics-server enabled."
