# Sandbox image for judged Python submissions. Built once via
# scripts/build-sandbox-images.ps1, referenced by tag from
# DockerSandboxService - not a docker-compose service, this image only ever
# backs short-lived sibling containers Judge Worker creates on demand.
FROM python:3.12-slim

# Non-root, no login shell - one of several defense-in-depth layers around
# submitted code (alongside CapDrop, NetworkMode:none, ReadonlyRootfs set by
# DockerSandboxService at container-create time, not here).
RUN useradd --no-create-home --shell /usr/sbin/nologin sandbox \
    && mkdir -p /sandbox \
    && chown sandbox:sandbox /sandbox
WORKDIR /sandbox
USER sandbox

# Keeps the container alive so Judge Worker can copy-archive the solution +
# generated driver in, then `docker exec` each test case against it - one
# container per submission, not a fresh one per test case. See the Phase 1
# plan's sibling-container section for why code arrives via copy-archive
# rather than a bind mount.
CMD ["sleep", "infinity"]
