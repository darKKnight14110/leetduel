package com.leetduel.judge.sandbox;

// exitCode is null when timedOut is true - the exec never returned an exit
// code because DockerSandboxService's hard safety-net deadline fired and
// force-killed the container before the daemon reported completion.
// exitCode is Long, matching InspectExecResponse.getExitCodeLong() -
// getExitCode() (Integer) is deprecated in docker-java.
public record ExecResult(Long exitCode, String stdout, String stderr, boolean timedOut) {

    // GNU coreutils `timeout` (the in-container, soft first layer) exits
    // 124 specifically to signal it killed the wrapped command - the
    // Docker-level hard safety net (timedOut=true) is a SEPARATE signal for
    // when even that itself didn't fire in time.
    public boolean isTimeLimitExceeded() {
        return timedOut || (exitCode != null && exitCode == 124L);
    }
}
