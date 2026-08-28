package com.leetduel.judge.job;

import com.leetduel.judge.event.SubmissionJudgedEvent;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.SeccompProfileBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@Profile("dispatcher")
@RequiredArgsConstructor
public class KubernetesJudgeDispatcher {

    private static final String RESULT_PREFIX = "LEETDUEL_RESULT:";
    private static final String JOB_KIND_LABEL = "leetduel.io/job-kind";
    private static final String JOB_KIND = "judge-executor";

    private final KubernetesClient kubernetesClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final JudgeEngine judgeEngine;

    @Value("${leetduel.events.judge-events-exchange}")
    private String judgeEventsExchange;

    @Value("${leetduel.events.submission-judged-routing-key}")
    private String submissionJudgedRoutingKey;

    @Value("${POD_NAMESPACE:default}")
    private String namespace;

    @Value("${JUDGE_EXECUTOR_IMAGE:leetduel-judge-executor:dev}")
    private String executorImage;

    @Value("${leetduel.judge.job-active-deadline-seconds:45}")
    private long activeDeadlineSeconds;

    @Value("${leetduel.judge.job-cpu-request:250m}")
    private String cpuRequest;

    @Value("${leetduel.judge.job-cpu-limit:1000m}")
    private String cpuLimit;

    @Value("${leetduel.judge.job-memory-request:128Mi}")
    private String memoryRequest;

    @Value("${leetduel.judge.job-memory-limit:512Mi}")
    private String memoryLimit;

    @RabbitListener(queues = "${leetduel.events.judge-jobs-queue}")
    public void onJudgeJob(JudgeJobCreatedEvent job) {
        String jobName = jobName(job);
        String inputName = inputConfigMapName(job);
        createOrReuseInput(inputName, job);
        createOrReuseJob(jobName, inputName, job);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileWhenReady() {
        reconcileCompletedJobs();
    }

    @Scheduled(fixedDelayString = "${leetduel.judge.reconcile-delay-ms:5000}")
    public synchronized void reconcileCompletedJobs() {
        List<Job> jobs = kubernetesClient.batch().v1().jobs()
                .inNamespace(namespace)
                .withLabel(JOB_KIND_LABEL, JOB_KIND)
                .list()
                .getItems();
        for (Job job : jobs) {
            if (!isCompleted(job)) {
                continue;
            }
            reconcileCompletedJob(job);
        }
    }

    private void reconcileCompletedJob(Job job) {
        String jobName = job.getMetadata().getName();
        String inputName = job.getSpec().getTemplate().getMetadata().getLabels().get("leetduel.io/input");
        ConfigMap input = kubernetesClient.configMaps().inNamespace(namespace).withName(inputName).get();
        if (input == null || input.getData() == null || input.getData().get("job.json") == null) {
            log.warn("Completed judge Job {} has no input ConfigMap yet", jobName);
            return;
        }

        JudgeJobCreatedEvent originalJob;
        try {
            originalJob = objectMapper.readValue(input.getData().get("job.json"), JudgeJobCreatedEvent.class);
        } catch (Exception e) {
            log.error("Could not decode input for completed judge Job {}", jobName, e);
            return;
        }

        SubmissionJudgedEvent result = readResult(jobName);
        if (result == null) {
            result = judgeEngine.internalError(originalJob);
        }

        rabbitTemplate.convertAndSend(judgeEventsExchange, submissionJudgedRoutingKey, result);
        kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        kubernetesClient.configMaps().inNamespace(namespace).withName(inputName).delete();
    }

    private SubmissionJudgedEvent readResult(String jobName) {
        List<Pod> pods = kubernetesClient.pods().inNamespace(namespace)
                .withLabel("job-name", jobName)
                .list()
                .getItems();
        for (Pod pod : pods) {
            String logs = kubernetesClient.pods().inNamespace(namespace)
                    .withName(pod.getMetadata().getName())
                    .getLog();
            if (logs == null) {
                continue;
            }
            SubmissionJudgedEvent result = parseFramedResult(logs);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    SubmissionJudgedEvent parseFramedResult(String logs) {
        String[] lines = logs.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            if (!lines[index].startsWith(RESULT_PREFIX)) {
                continue;
            }
            try {
                return objectMapper.readValue(lines[index].substring(RESULT_PREFIX.length()),
                        SubmissionJudgedEvent.class);
            } catch (Exception exception) {
                log.warn("Malformed framed result in judge Job logs", exception);
                return null;
            }
        }
        return null;
    }

    private void createOrReuseInput(String inputName, JudgeJobCreatedEvent job) {
        ConfigMap configMap = buildInputConfigMap(inputName, job);
        try {
            kubernetesClient.configMaps().inNamespace(namespace).resource(configMap).create();
        } catch (KubernetesClientException exception) {
            if (!isAlreadyExists(exception)) {
                throw exception;
            }
        }
    }

    ConfigMap buildInputConfigMap(String inputName, JudgeJobCreatedEvent job) {
        return new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(inputName)
                        .withNamespace(namespace)
                        .withLabels(labels(job))
                        .build())
                .withImmutable(true)
                .withData(Map.of("job.json", serialize(job)))
                .build();
    }

    private void createOrReuseJob(String jobName, String inputName, JudgeJobCreatedEvent job) {
        Job executorJob = buildExecutorJob(jobName, inputName, job);
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(executorJob).create();
        } catch (KubernetesClientException exception) {
            if (!isAlreadyExists(exception)) {
                throw exception;
            }
        }
    }

    Job buildExecutorJob(String jobName, String inputName, JudgeJobCreatedEvent job) {
        Map<String, String> labels = labels(job);
        Map<String, String> podLabels = new HashMap<>(labels);
        podLabels.put("leetduel.io/input", inputName);
        return new JobBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(jobName)
                        .withNamespace(namespace)
                        .withLabels(labels)
                        .build())
                .withSpec(new JobSpecBuilder()
                        .withBackoffLimit(0)
                        .withActiveDeadlineSeconds(activeDeadlineSeconds)
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(podLabels)
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withRestartPolicy("Never")
                                        .withAutomountServiceAccountToken(false)
                                        .withSecurityContext(new PodSecurityContextBuilder()
                                                .withRunAsNonRoot(true)
                                                .withRunAsUser(10001L)
                                                .withRunAsGroup(10001L)
                                                .withFsGroup(10001L)
                                                .build())
                                        .withContainers(new ContainerBuilder()
                                                .withName("judge-executor")
                                                .withImage(executorImage)
                                                .withImagePullPolicy("IfNotPresent")
                                                .withArgs(
                                                        "--spring.profiles.active=executor",
                                                        "--spring.main.web-application-type=none",
                                                        "--leetduel.judge.input-file=/config/job.json")
                                                .withResources(new ResourceRequirementsBuilder()
                                                        .withRequests(Map.of(
                                                                "cpu", new Quantity(cpuRequest),
                                                                "memory", new Quantity(memoryRequest)))
                                                        .withLimits(Map.of(
                                                                "cpu", new Quantity(cpuLimit),
                                                                "memory", new Quantity(memoryLimit)))
                                                        .build())
                                                .withSecurityContext(new SecurityContextBuilder()
                                                        .withRunAsNonRoot(true)
                                                        .withRunAsUser(10001L)
                                                        .withAllowPrivilegeEscalation(false)
                                                        .withReadOnlyRootFilesystem(true)
                                                        .withCapabilities(new CapabilitiesBuilder().withDrop("ALL").build())
                                                        .withSeccompProfile(new SeccompProfileBuilder()
                                                                .withType("RuntimeDefault")
                                                                .build())
                                                        .build())
                                                .withVolumeMounts(
                                                        new VolumeMountBuilder()
                                                                .withName("input")
                                                                .withMountPath("/config")
                                                                .withReadOnly(true)
                                                                .build(),
                                                        new VolumeMountBuilder()
                                                                .withName("sandbox")
                                                                .withMountPath("/sandbox")
                                                                .build(),
                                                        new VolumeMountBuilder()
                                                                .withName("tmp")
                                                                .withMountPath("/tmp")
                                                                .build())
                                                .build())
                                        .withVolumes(
                                                new VolumeBuilder()
                                                        .withName("input")
                                                        .withConfigMap(new ConfigMapVolumeSourceBuilder()
                                                                .withName(inputName)
                                                                .withDefaultMode(0444)
                                                                .build())
                                                        .build(),
                                                new VolumeBuilder()
                                                        .withName("sandbox")
                                                        .withEmptyDir(new EmptyDirVolumeSourceBuilder().build())
                                                        .build(),
                                                new VolumeBuilder()
                                                        .withName("tmp")
                                                        .withEmptyDir(new EmptyDirVolumeSourceBuilder().build())
                                                        .build())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private boolean isCompleted(Job job) {
        if (job.getStatus() == null) {
            return false;
        }
        Integer succeeded = job.getStatus().getSucceeded();
        Integer failed = job.getStatus().getFailed();
        return (succeeded != null && succeeded > 0) || (failed != null && failed > 0);
    }

    private String serialize(JudgeJobCreatedEvent job) {
        try {
            return objectMapper.writeValueAsString(job);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize judge input", exception);
        }
    }

    private Map<String, String> labels(JudgeJobCreatedEvent job) {
        return Map.of(
                "app.kubernetes.io/name", "judge-executor",
                JOB_KIND_LABEL, JOB_KIND,
                "leetduel.io/submission-id", job.submissionId().toString());
    }

    private String jobName(JudgeJobCreatedEvent job) {
        return "judge-" + job.submissionId();
    }

    private String inputConfigMapName(JudgeJobCreatedEvent job) {
        return "judge-input-" + job.submissionId();
    }

    private boolean isAlreadyExists(KubernetesClientException exception) {
        return exception.getCode() == 409;
    }
}
