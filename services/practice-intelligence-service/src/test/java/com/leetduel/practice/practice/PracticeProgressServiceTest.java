package com.leetduel.practice.practice;

import com.leetduel.practice.event.PracticeSubmissionCompletedEvent;
import com.leetduel.practice.repository.PracticeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;

@ExtendWith(MockitoExtension.class)
class PracticeProgressServiceTest {

    @Mock
    private PracticeRepository practiceRepository;

    @Test
    void duplicateDeliveryIsReportedAsNoOp() {
        PracticeProgressService service = new PracticeProgressService(practiceRepository);
        PracticeSubmissionCompletedEvent event = new PracticeSubmissionCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "PYTHON", "code", "ACCEPTED", 2, 2, "[]", Instant.now());
        when(practiceRepository.recordAttempt(any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(false);

        org.assertj.core.api.Assertions.assertThat(service.record(event)).isFalse();
    }
}
