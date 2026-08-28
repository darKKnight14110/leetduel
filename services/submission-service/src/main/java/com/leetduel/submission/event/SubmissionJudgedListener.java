package com.leetduel.submission.event;

import com.leetduel.submission.submission.Submission;
import com.leetduel.submission.submission.SubmissionRepository;
import com.leetduel.submission.submission.SubmissionStatus;
import com.leetduel.submission.submission.Verdict;
import com.leetduel.submission.outbox.OutboxEvent;
import com.leetduel.submission.outbox.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Idempotent consumer, same defensive shape as user-service's
// UserCreatedListener: RabbitMQ is at-least-once, so this may run more than
// once for the same submissionId (redelivery after a crash/ack timeout, or
// - unique to this event, since Judge Worker is stateless and holds no
// dedup state of its own - a full sandbox re-run republishing the same
// result). Checking status != JUDGED before applying covers both cases
// cheaply: a duplicate delivery after the row is already terminal is a
// no-op, never a double-apply.
@Slf4j
@Component
public class SubmissionJudgedListener {

    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Autowired
    public SubmissionJudgedListener(SubmissionRepository submissionRepository, ObjectMapper objectMapper,
            OutboxEventRepository outboxEventRepository) {
        this.submissionRepository = submissionRepository;
        this.objectMapper = objectMapper;
        this.outboxEventRepository = outboxEventRepository;
    }

    public SubmissionJudgedListener(SubmissionRepository submissionRepository, ObjectMapper objectMapper) {
        this(submissionRepository, objectMapper, null);
    }

    @RabbitListener(queues = "${leetduel.events.submission-judged-queue}")
    @Transactional
    public void onSubmissionJudged(SubmissionJudgedEvent event) {
        Optional<Submission> maybeSubmission = submissionRepository.findById(event.submissionId());
        if (maybeSubmission.isEmpty()) {
            log.warn("Received submission.judged for unknown submission {}", event.submissionId());
            return;
        }

        Submission submission = maybeSubmission.get();
        if (submission.getStatus() == SubmissionStatus.JUDGED) {
            log.debug("Submission {} already judged, skipping (duplicate delivery)", event.submissionId());
            return;
        }

        submission.setStatus(SubmissionStatus.JUDGED);
        submission.setVerdict(Verdict.valueOf(event.verdict()));
        submission.setTestCasesPassed(event.testCasesPassed());
        submission.setTestCasesTotal(event.testCasesTotal());
        submission.setTestResults(objectMapper.writeValueAsString(sanitizeResults(event)));
        Instant judgedAt = Instant.now();
        submission.setJudgedAt(judgedAt);
        submissionRepository.save(submission);

        if (submission.getMatchId() == null && outboxEventRepository != null) {
            PracticeSubmissionCompletedEvent practiceEvent = new PracticeSubmissionCompletedEvent(
                    submission.getId(), submission.getUserId(), submission.getProblemId(),
                    submission.getLanguage().name(), submission.getSourceCode(), event.verdict(),
                    event.testCasesPassed(), event.testCasesTotal(),
                    objectMapper.writeValueAsString(sanitizeDiagnostics(event)), judgedAt);
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setEventType("practice.submission.completed");
            outboxEvent.setPayload(objectMapper.writeValueAsString(practiceEvent));
            outboxEventRepository.save(outboxEvent);
        }
    }

    private List<SubmissionJudgedEvent.TestCaseResultPayload> sanitizeResults(SubmissionJudgedEvent event) {
        return event.testResults().stream()
                .map(result -> result.sample()
                        ? result
                        : new SubmissionJudgedEvent.TestCaseResultPayload(result.ordinal(), result.status(),
                                result.runtimeMs(), null, null, false))
                .toList();
    }

    private List<SubmissionJudgedEvent.TestCaseResultPayload> sanitizeDiagnostics(SubmissionJudgedEvent event) {
        return event.testResults().stream()
                .map(result -> new SubmissionJudgedEvent.TestCaseResultPayload(result.ordinal(), result.status(),
                        result.runtimeMs(), null, null, result.sample()))
                .toList();
    }
}
