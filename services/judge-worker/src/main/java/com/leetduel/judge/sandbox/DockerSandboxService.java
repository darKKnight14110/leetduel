package com.leetduel.judge.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Owns the sandbox container lifecycle: create (with every resource limit
// applied at create time, never left to a default), write code in via an
// attached exec's stdin (not copyArchiveToContainerCmd - see writeFile's
// comment for why that API doesn't work here), exec each test case with a
// two-layer timeout, and remove. One container per submission - see
// JudgeJobListener for why callers create exactly one and exec repeatedly
// against it rather than one container per test case.
@Slf4j
@Service
public class DockerSandboxService {

    private static final int WRITE_FILE_TIMEOUT_MS = 5000;

    private final DockerClient dockerClient;
    private final long memoryBytes;
    private final long nanoCpus;
    private final long pidsLimit;
    private final long timeoutBufferMs;

    public DockerSandboxService(
            DockerClient dockerClient,
            @Value("${leetduel.sandbox.memory-bytes}") long memoryBytes,
            @Value("${leetduel.sandbox.nano-cpus}") long nanoCpus,
            @Value("${leetduel.sandbox.pids-limit}") long pidsLimit,
            @Value("${leetduel.sandbox.timeout-buffer-ms}") long timeoutBufferMs) {
        this.dockerClient = dockerClient;
        this.memoryBytes = memoryBytes;
        this.nanoCpus = nanoCpus;
        this.pidsLimit = pidsLimit;
        this.timeoutBufferMs = timeoutBufferMs;
    }

    // ReadonlyRootfs + a writable tmpfs at /sandbox and /tmp is the
    // deliberate combination that keeps the image's own layers immutable
    // (nothing a submission does can persist past container removal)
    // while still letting the harness write compiled .class files -
    // dropping ReadonlyRootfs entirely to sidestep that would have been
    // the silent-simplification CLAUDE.md warns against; a tmpfs mount is
    // the correct fix, not a shortcut. See writeFile for the consequence
    // this has on how code gets INTO the container in the first place.
    // mode=1777 is required on /sandbox (confirmed against the real
    // daemon): Docker only gives its OWN hardcoded default of 1777 to the
    // /tmp tmpfs mount specifically - any other tmpfs path with no
    // explicit mode= mounts as root:root 0755, which the non-root
    // `sandbox` image user then can't write into at all.
    public String createContainer(String image) {
        HostConfig hostConfig = new HostConfig()
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withNanoCPUs(nanoCpus)
                .withNetworkMode("none")
                .withPidsLimit(pidsLimit)
                .withReadonlyRootfs(true)
                .withCapDrop(Capability.ALL)
                .withTmpFs(Map.of(
                        "/tmp", "rw,noexec,nosuid,size=67108864",
                        "/sandbox", "rw,noexec,nosuid,size=67108864,mode=1777"));

        CreateContainerResponse response = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .exec();
        dockerClient.startContainerCmd(response.getId()).exec();
        return response.getId();
    }

    public void copyFiles(String containerId, Map<String, String> filenameToContent) {
        for (Map.Entry<String, String> entry : filenameToContent.entrySet()) {
            writeFile(containerId, entry.getKey(), entry.getValue());
        }
    }

    // copyArchiveToContainerCmd - the sibling-container-safe docker-cp
    // equivalent this project otherwise wants for getting code into a
    // container without a host bind mount - unconditionally rejects any
    // write into a container whose HostConfig has ReadonlyRootfs:true
    // ("container rootfs is marked read-only"), even when the destination
    // is itself a writable tmpfs mount. This is a real Moby engine
    // limitation, confirmed against the actual daemon, not a bug in this
    // code: the archive-copy endpoint refuses outright rather than
    // checking whether the target path is a separate writable mount.
    //
    // The fix is still an exec, just not one driven over an attached
    // stdin: a first attempt piped file content through
    // ExecStartCmd.withStdIn(InputStream) into `cat > file`, but
    // confirmed against the real daemon, docker-java's zerodep transport
    // never signals EOF to the container process once the InputStream is
    // exhausted - `cat` blocks forever waiting for more input and the
    // exec times out every time. Passing the content instead as a
    // base64-encoded command-line argument (`echo '<b64>' | base64 -d >
    // file`) needs no attached stdin at all, sidestepping that transport
    // gap entirely while still never touching the archive endpoint or a
    // host path (still sibling-container-safe) and still keeping
    // ReadonlyRootfs intact (see createContainer's comment for why that
    // flag isn't negotiable). Bounded on purpose to submission-sized
    // source files - base64 inflates size ~4/3 and lands well under any
    // shell ARG_MAX for anything LeetCode-scale.
    private void writeFile(String containerId, String filename, String content) {
        String base64Content = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        String writeCommand = "echo '" + base64Content + "' | base64 -d > /sandbox/" + filename;

        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("sh", "-c", writeCommand)
                .exec();

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame.getStreamType() == StreamType.STDERR) {
                    stderr.writeBytes(frame.getPayload());
                }
            }
        };

        boolean completed;
        try {
            dockerClient.execStartCmd(execCreate.getId()).exec(callback);
            completed = callback.awaitCompletion(WRITE_FILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while writing " + filename
                    + " into sandbox container " + containerId, e);
        }

        if (!completed) {
            throw new SandboxException("Timed out writing " + filename
                    + " into sandbox container " + containerId);
        }

        Long exitCode = dockerClient.inspectExecCmd(execCreate.getId()).exec().getExitCodeLong();
        if (exitCode == null || exitCode != 0) {
            throw new SandboxException("Writing " + filename + " into sandbox container " + containerId
                    + " failed (exit " + exitCode + "): " + stderr.toString(StandardCharsets.UTF_8));
        }
    }

    // Two-layer timeout: the in-container `timeout` wrapper is the soft,
    // expected mechanism (exit 124 on its own trigger - see
    // ExecResult.isTimeLimitExceeded). awaitCompletion's own deadline
    // (timeLimitMs + a fixed buffer) is the hard safety net for when even
    // `timeout` itself hangs (e.g. a submission spawns a process that
    // ignores SIGTERM) - never trust one kill mechanism alone. A hard
    // timeout here always ends this container's judging; the caller must
    // treat it the same as any other terminal failure.
    public ExecResult exec(String containerId, List<String> command, int timeLimitMs) {
        List<String> wrapped = new ArrayList<>();
        wrapped.add("timeout");
        wrapped.add(String.format("%.3f", timeLimitMs / 1000.0));
        wrapped.addAll(command);

        ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(wrapped.toArray(new String[0]))
                .exec();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        // ResultCallback.Adapter<Frame>, not the deprecated
        // ExecStartResultCallback - same awaitCompletion(timeout, unit)
        // this two-layer timeout depends on, just routing each Frame's
        // payload to the right stream by hand instead of the deprecated
        // class doing it internally.
        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame.getStreamType() == StreamType.STDERR) {
                    stderr.writeBytes(frame.getPayload());
                } else {
                    stdout.writeBytes(frame.getPayload());
                }
            }
        };
        dockerClient.execStartCmd(execCreate.getId()).exec(callback);

        boolean completed;
        try {
            completed = callback.awaitCompletion(timeLimitMs + timeoutBufferMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while waiting for sandbox exec to complete", e);
        }

        if (!completed) {
            log.warn("Exec on container {} exceeded hard deadline, force-killing container", containerId);
            killQuietly(containerId);
            return new ExecResult(null, stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8), true);
        }

        Long exitCode = dockerClient.inspectExecCmd(execCreate.getId()).exec().getExitCodeLong();
        return new ExecResult(exitCode, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8), false);
    }

    public void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        } catch (Exception e) {
            log.warn("Failed to remove sandbox container {}", containerId, e);
        }
    }

    private void killQuietly(String containerId) {
        try {
            dockerClient.killContainerCmd(containerId).exec();
        } catch (Exception e) {
            log.warn("Failed to kill container {} after hard timeout", containerId, e);
        }
    }
}
