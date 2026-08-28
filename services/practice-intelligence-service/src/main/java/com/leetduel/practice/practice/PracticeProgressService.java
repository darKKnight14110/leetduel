package com.leetduel.practice.practice;

import com.leetduel.practice.event.PracticeSubmissionCompletedEvent;
import com.leetduel.practice.repository.PracticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PracticeProgressService {

    private final PracticeRepository practiceRepository;

    @Transactional
    public boolean record(PracticeSubmissionCompletedEvent event) {
        return practiceRepository.recordAttempt(event.submissionId(), event.userId(), event.problemId(),
                event.language(), event.verdict(), event.testCasesPassed(), event.testCasesTotal(), event.judgedAt(),
                event.sourceCode(), event.diagnostics());
    }
}
