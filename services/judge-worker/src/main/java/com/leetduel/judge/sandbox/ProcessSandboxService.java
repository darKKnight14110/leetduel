package com.leetduel.judge.sandbox;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@Profile("executor")
public class ProcessSandboxService implements SandboxSession {

    private static final Path WORK_DIRECTORY = Path.of("/sandbox");
    private static final int WRITE_FILE_TIMEOUT_MS = 5000;
    private static final int TIMEOUT_BUFFER_MS = 2000;

    @Override
    public void copyFiles(Map<String, String> filenameToContent) {
        filenameToContent.forEach((filename, content) -> {
            if (!filename.matches("[A-Za-z0-9._-]+")) {
                throw new SandboxException("Invalid sandbox filename: " + filename);
            }
            try {
                Path target = WORK_DIRECTORY.resolve(filename).normalize();
                if (!target.getParent().equals(WORK_DIRECTORY)) {
                    throw new SandboxException("Sandbox filename escapes work directory: " + filename);
                }
                Files.writeString(target, content, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new SandboxException("Could not write " + filename + " into the executor sandbox", e);
            }
        });
    }

    @Override
    public ExecResult exec(List<String> command, int timeLimitMs) {
        List<String> wrapped = new ArrayList<>();
        wrapped.add("timeout");
        wrapped.add(String.format("%.3f", timeLimitMs / 1000.0));
        wrapped.addAll(command);

        try {
            Process process = new ProcessBuilder(wrapped)
                    .directory(WORK_DIRECTORY.toFile())
                    .start();
            try (var outputExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> stdout = outputExecutor.submit(() -> capture(process.getInputStream()));
                Future<String> stderr = outputExecutor.submit(() -> capture(process.getErrorStream()));
                boolean completed = process.waitFor(timeLimitMs + TIMEOUT_BUFFER_MS, TimeUnit.MILLISECONDS);
                if (!completed) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    process.waitFor(WRITE_FILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    return new ExecResult(null, readOutput(stdout), readOutput(stderr), true);
                }
                return new ExecResult((long) process.exitValue(), readOutput(stdout), readOutput(stderr), false);
            }
        } catch (IOException e) {
            throw new SandboxException("Could not start sandbox command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while waiting for sandbox command", e);
        }
    }

    private String capture(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SandboxException("Could not capture sandbox output", e);
        }
    }

    private String readOutput(Future<String> output) {
        try {
            return output.get(WRITE_FILE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("Interrupted while capturing sandbox output", e);
        } catch (ExecutionException e) {
            throw new SandboxException("Could not capture sandbox output", e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw new SandboxException("Timed out capturing sandbox output", e);
        }
    }

    @Override
    public void close() {
        try (var files = Files.walk(WORK_DIRECTORY)) {
            files.filter(path -> !path.equals(WORK_DIRECTORY))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new SandboxException("Could not clean executor sandbox", e);
                }
                    });
        } catch (IOException e) {
            throw new SandboxException("Could not inspect executor sandbox", e);
        }
    }
}
