package com.leetduel.submission.submission;

import com.leetduel.submission.dto.CreateSubmissionRequest;
import com.leetduel.submission.dto.InternalProblemDetailResponse;
import com.leetduel.submission.event.JudgeJobCreatedEvent;
import com.leetduel.submission.outbox.OutboxEvent;
import com.leetduel.submission.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

// Split out of SubmissionService as its own bean specifically so the
// @Transactional boundary below never wraps ProblemServiceClient's network
// call - Spring's self-invocation rule means an @Transactional method
// called via "this." from within the same class silently runs with NO
// transaction at all, so the two DB writes (submission + outbox row) that
// MUST be atomic have to live in a genuinely separate bean, called from
// outside via the proxy.
@Component
@RequiredArgsConstructor
class SubmissionWriter {

    private final SubmissionRepository submissionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    UUID persist(UUID userId, CreateSubmissionRequest request, InternalProblemDetailResponse problemDetail) {
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setProblemId(request.problemId());
        submission.setMatchId(request.matchId());
        submission.setLanguage(request.language());
        submission.setSourceCode(request.sourceCode());
        submission = submissionRepository.save(submission);

        JudgeJobCreatedEvent event = toJobEvent(submission, problemDetail);
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType("judge.job.created");
        outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        outboxEventRepository.save(outboxEvent);

        return submission.getId();
    }

    private JudgeJobCreatedEvent toJobEvent(Submission submission, InternalProblemDetailResponse problemDetail) {
        List<JudgeJobCreatedEvent.ParameterPayload> parameters = problemDetail.parameters().stream()
                .map(p -> new JudgeJobCreatedEvent.ParameterPayload(p.name(), p.type()))
                .toList();
        List<JudgeJobCreatedEvent.TestCasePayload> testCases = problemDetail.testCases().stream()
                .map(tc -> new JudgeJobCreatedEvent.TestCasePayload(tc.ordinal(), tc.input(), tc.expectedOutput(), tc.sample()))
                .toList();

        return new JudgeJobCreatedEvent(
                submission.getId(), submission.getProblemId(), submission.getUserId(), submission.getMatchId(),
                submission.getLanguage().name(), submission.getSourceCode(),
                problemDetail.functionName(), problemDetail.returnType(), parameters,
                problemDetail.timeLimitMs(), problemDetail.memoryLimitMb(), testCases);
    }
}
