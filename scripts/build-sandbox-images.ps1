# Builds the two sandbox images DockerSandboxService references by tag.
# Not docker-compose services - these only ever back short-lived sibling
# containers Judge Worker creates on demand per submission. Re-run after
# editing either Dockerfile; image tags are fixed (":latest"), so a rebuild
# simply replaces what's referenced next time a container is created.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

docker build -t leetduel-sandbox-python:latest "$root/docker/sandbox-python"
docker build -t leetduel-sandbox-java:latest "$root/docker/sandbox-java"

Write-Host "Built leetduel-sandbox-python:latest and leetduel-sandbox-java:latest"
