package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import com.leetduel.judge.sandbox.ProcessSandboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
@Profile("executor")
@RequiredArgsConstructor
public class KubernetesJudgeExecutor implements CommandLineRunner {

    private static final String RESULT_PREFIX = "LEETDUEL_RESULT:";

    private final ObjectMapper objectMapper;
    private final JudgeEngine judgeEngine;
    private final ProcessSandboxService sandboxService;

    @Value("${leetduel.judge.input-file:/config/job.json}")
    private String inputFile;

    @Override
    public void run(String... args) {
        JudgeJobCreatedEvent job = null;
        try {
            job = objectMapper.readValue(Files.readString(Path.of(inputFile)), JudgeJobCreatedEvent.class);
            SubmissionJudgedEvent result;
            try (sandboxService) {
                result = judgeEngine.judge(job, sandboxService);
            }
            emit(result);
        } catch (Exception exception) {
            log.error("Judge executor failed", exception);
            if (job != null) {
                emit(judgeEngine.internalError(job));
            }
        }
    }

    private void emit(SubmissionJudgedEvent result) {
        try {
            System.out.println(RESULT_PREFIX + objectMapper.writeValueAsString(result));
            System.out.flush();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not frame judge result", exception);
        }
    }
}
