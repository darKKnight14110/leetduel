package com.leetduel.submission.event;

import com.leetduel.submission.submission.Language;
import com.leetduel.submission.submission.Submission;
import com.leetduel.submission.submission.SubmissionRepository;
import com.leetduel.submission.submission.SubmissionStatus;
import com.leetduel.submission.submission.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionJudgedListenerTest {

    @Mock
    private SubmissionRepository submissionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SubmissionJudgedListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubmissionJudgedListener(submissionRepository, objectMapper);
    }

    private Submission pendingSubmission(UUID id) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setUserId(UUID.randomUUID());
        submission.setProblemId(UUID.randomUUID());
        submission.setLanguage(Language.PYTHON);
        submission.setSourceCode("code");
        submission.setStatus(SubmissionStatus.PENDING);
        return submission;
    }

    @Test
    void onSubmissionJudged_updatesSubmissionToJudged_whenPending() {
        // Arrange
        UUID submissionId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(submissionId, "ACCEPTED", 3, 3, List.of(
                new SubmissionJudgedEvent.TestCaseResultPayload(0, "PASSED", 12L, null, null)));

        // Act
        listener.onSubmissionJudged(event);

        // Assert
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(captor.capture());
        Submission saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.JUDGED);
        assertThat(saved.getVerdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(saved.getTestCasesPassed()).isEqualTo(3);
    }

    @Test
    void onSubmissionJudged_redactsHiddenExpectedAndActualOutput() {
        UUID submissionId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(submissionId, null, null, "WRONG_ANSWER", 1, 2, List.of(
                new SubmissionJudgedEvent.TestCaseResultPayload(0, "WRONG_ANSWER", 8L, "public", "public-actual", true),
                new SubmissionJudgedEvent.TestCaseResultPayload(1, "WRONG_ANSWER", 9L, "hidden-answer", "hidden-actual", false)));

        listener.onSubmissionJudged(event);

        assertThat(submission.getTestResults()).contains("public").doesNotContain("hidden-answer").doesNotContain("hidden-actual");
    }

    @Test
    void onSubmissionJudged_skipsUpdate_whenAlreadyJudged() {
        // Arrange - simulates redelivery of the same event, or a re-run
        // sandbox republishing the same result (Judge Worker is stateless).
        UUID submissionId = UUID.randomUUID();
        Submission submission = pendingSubmission(submissionId);
        submission.setStatus(SubmissionStatus.JUDGED);
        submission.setVerdict(Verdict.ACCEPTED);
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(submissionId, "WRONG_ANSWER", 1, 3, List.of());

        // Act
        listener.onSubmissionJudged(event);

        // Assert - must not overwrite an already-terminal result.
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void onSubmissionJudged_doesNothing_whenSubmissionUnknown() {
        // Arrange
        UUID submissionId = UUID.randomUUID();
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());
        SubmissionJudgedEvent event = new SubmissionJudgedEvent(submissionId, "ACCEPTED", 1, 1, List.of());

        // Act & Assert - must not throw (would nack and trigger endless
        // redelivery).
        listener.onSubmissionJudged(event);
        verify(submissionRepository, never()).save(any());
    }
}
