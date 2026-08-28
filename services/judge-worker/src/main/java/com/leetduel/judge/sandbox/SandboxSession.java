package com.leetduel.judge.sandbox;

import java.util.List;
import java.util.Map;

public interface SandboxSession extends AutoCloseable {

    void copyFiles(Map<String, String> filenameToContent);

    ExecResult exec(List<String> command, int timeLimitMs);

    @Override
    void close();
}
