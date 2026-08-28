package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class KubernetesJudgeDispatcherTest {

    @Mock
    private KubernetesClient kubernetesClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KubernetesJudgeDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new KubernetesJudgeDispatcher(kubernetesClient, mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class),
                objectMapper, mock(JudgeEngine.class));
        ReflectionTestUtils.setField(dispatcher, "namespace", "leetduel");
        ReflectionTestUtils.setField(dispatcher, "executorImage", "leetduel-judge-executor:dev");
        ReflectionTestUtils.setField(dispatcher, "activeDeadlineSeconds", 45L);
        ReflectionTestUtils.setField(dispatcher, "cpuRequest", "250m");
        ReflectionTestUtils.setField(dispatcher, "cpuLimit", "1000m");
        ReflectionTestUtils.setField(dispatcher, "memoryRequest", "128Mi");
        ReflectionTestUtils.setField(dispatcher, "memoryLimit", "512Mi");
    }

    @Test
    void buildsImmutableInputAndDeterministicSecureExecutorJob() {
        UUID submissionId = UUID.randomUUID();
        JudgeJobCreatedEvent job = job(submissionId);

        ConfigMap input = dispatcher.buildInputConfigMap("judge-input-" + submissionId, job);
        Job executorJob = dispatcher.buildExecutorJob("judge-" + submissionId, input.getMetadata().getName(), job);

        assertThat(input.getMetadata().getName()).isEqualTo("judge-input-" + submissionId);
        assertThat(input.getImmutable()).isTrue();
        assertThat(input.getData()).containsKey("job.json");
        assertThat(executorJob.getMetadata().getName()).isEqualTo("judge-" + submissionId);
        assertThat(executorJob.getSpec().getBackoffLimit()).isZero();
        assertThat(executorJob.getSpec().getActiveDeadlineSeconds()).isEqualTo(45L);

        PodSpec podSpec = executorJob.getSpec().getTemplate().getSpec();
        Container container = podSpec.getContainers().get(0);
        SecurityContext securityContext = container.getSecurityContext();
        assertThat(container.getImage()).isEqualTo("leetduel-judge-executor:dev");
        assertThat(container.getArgs()).contains("--spring.profiles.active=executor");
        assertThat(podSpec.getAutomountServiceAccountToken()).isFalse();
        assertThat(securityContext.getRunAsNonRoot()).isTrue();
        assertThat(securityContext.getAllowPrivilegeEscalation()).isFalse();
        assertThat(securityContext.getReadOnlyRootFilesystem()).isTrue();
        assertThat(securityContext.getCapabilities().getDrop()).contains("ALL");
        assertThat(securityContext.getSeccompProfile().getType()).isEqualTo("RuntimeDefault");
        assertThat(container.getVolumeMounts()).extracting("mountPath")
                .containsExactlyInAnyOrder("/config", "/sandbox", "/tmp");
    }

    @Test
    void parsesLastFramedResultAndIgnoresOtherPodLogs() throws Exception {
        SubmissionJudgedEvent result = new SubmissionJudgedEvent(
                UUID.randomUUID(), null, UUID.randomUUID(), "ACCEPTED", 1, 1, List.of());
        String logs = "Spring startup\nLEETDUEL_RESULT:" + objectMapper.writeValueAsString(result) + "\n";

        assertThat(dispatcher.parseFramedResult(logs)).isEqualTo(result);
    }

    @Test
    void returnsNoResultForMalformedFrame() {
        assertThat(dispatcher.parseFramedResult("LEETDUEL_RESULT:not-json")).isNull();
    }

    private JudgeJobCreatedEvent job(UUID submissionId) {
        return new JudgeJobCreatedEvent(
                submissionId, UUID.randomUUID(), UUID.randomUUID(), null,
                "PYTHON", "def f(x): return x", "f", "int[]",
                List.of(new JudgeJobCreatedEvent.ParameterPayload("x", "int[]")),
                1000, 256,
                List.of(new JudgeJobCreatedEvent.TestCasePayload(0, "[1]", "[1]")));
    }
}
