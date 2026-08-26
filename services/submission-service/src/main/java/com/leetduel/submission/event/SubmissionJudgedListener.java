package com.leetduel.submission.event;

import com.leetduel.submission.submission.Submission;
import com.leetduel.submission.submission.SubmissionRepository;
import com.leetduel.submission.submission.SubmissionStatus;
import com.leetduel.submission.submission.Verdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
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
@RequiredArgsConstructor
public class SubmissionJudgedListener {

    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${leetduel.events.submission-judged-queue}")
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
        submission.setTestResults(objectMapper.writeValueAsString(event.testResults()));
        submission.setJudgedAt(Instant.now());
        submissionRepository.save(submission);
    }
}
