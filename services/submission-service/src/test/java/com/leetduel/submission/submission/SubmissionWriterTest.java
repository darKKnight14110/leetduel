package com.leetduel.submission.submission;

import com.leetduel.submission.dto.CreateSubmissionRequest;
import com.leetduel.submission.dto.InternalProblemDetailResponse;
import com.leetduel.submission.outbox.OutboxEvent;
import com.leetduel.submission.outbox.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionWriterTest {

    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SubmissionWriter submissionWriter;

    @BeforeEach
    void setUp() {
        submissionWriter = new SubmissionWriter(submissionRepository, outboxEventRepository, objectMapper);
        when(submissionRepository.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
    }

    @Test
    void persist_savesSubmissionAndOutboxEventInOneCall() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        CreateSubmissionRequest request = new CreateSubmissionRequest(problemId, Language.PYTHON, "def f(): pass");
        InternalProblemDetailResponse problemDetail = new InternalProblemDetailResponse(
                problemId, "twoSum", "int[]",
                List.of(new InternalProblemDetailResponse.ParameterResponse("nums", "int[]")),
                2000, 256,
                List.of(new InternalProblemDetailResponse.TestCaseResponse(0, "[[2,7,11,15],9]", "[0,1]")));

        // Act
        UUID submissionId = submissionWriter.persist(userId, request, problemDetail);

        // Assert
        assertThat(submissionId).isNotNull();
        verify(submissionRepository).save(any());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo("judge.job.created");
        assertThat(event.getPayload()).contains("twoSum").contains("nums");
    }
}
