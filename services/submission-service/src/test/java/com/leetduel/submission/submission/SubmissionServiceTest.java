package com.leetduel.submission.submission;

import com.leetduel.submission.client.ProblemServiceClient;
import com.leetduel.submission.dto.CreateSubmissionRequest;
import com.leetduel.submission.dto.InternalProblemDetailResponse;
import com.leetduel.submission.dto.SubmissionResponse;
import com.leetduel.submission.exception.ProblemServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private ProblemServiceClient problemServiceClient;
    @Mock
    private SubmissionWriter submissionWriter;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionService(submissionRepository, problemServiceClient, submissionWriter);
    }

    @Test
    void submitCode_fetchesProblemDetailThenPersistsThenReturnsSavedSubmission() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        CreateSubmissionRequest request = new CreateSubmissionRequest(problemId, Language.PYTHON, "def f(): pass");
        InternalProblemDetailResponse problemDetail = new InternalProblemDetailResponse(
                problemId, "twoSum", "int[]", List.of(), 2000, 256, List.of());
        when(problemServiceClient.getTestCases(problemId)).thenReturn(problemDetail);
        when(submissionWriter.persist(userId, request, problemDetail)).thenReturn(submissionId);

        Submission saved = new Submission();
        saved.setId(submissionId);
        saved.setUserId(userId);
        saved.setProblemId(problemId);
        saved.setLanguage(Language.PYTHON);
        saved.setSourceCode(request.sourceCode());
        when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(saved));

        // Act
        SubmissionResponse response = submissionService.submitCode(userId, request);

        // Assert
        assertThat(response.id()).isEqualTo(submissionId);
        assertThat(response.status()).isEqualTo(SubmissionStatus.PENDING);
    }

    @Test
    void submitCode_neverPersists_whenProblemServiceIsUnreachable() {
        // Arrange - a failed upstream fetch must not leave an orphaned
        // submission row with no matching job ever dispatched.
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        CreateSubmissionRequest request = new CreateSubmissionRequest(problemId, Language.PYTHON, "def f(): pass");
        when(problemServiceClient.getTestCases(problemId))
                .thenThrow(new ProblemServiceUnavailableException("down", null));

        // Act & Assert
        assertThatThrownBy(() -> submissionService.submitCode(userId, request))
                .isInstanceOf(ProblemServiceUnavailableException.class);
        verifyNoInteractions(submissionWriter);
    }
}
