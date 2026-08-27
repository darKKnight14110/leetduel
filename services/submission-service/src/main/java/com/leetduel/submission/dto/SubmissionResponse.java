package com.leetduel.submission.dto;

import com.leetduel.submission.submission.Language;
import com.leetduel.submission.submission.Submission;
import com.leetduel.submission.submission.SubmissionStatus;
import com.leetduel.submission.submission.Verdict;

import java.time.Instant;
import java.util.UUID;

// Reads entirely from this service's own row - no cross-service call at
// read time, since Judge Worker is stateless and holds nothing to query.
public record SubmissionResponse(
        UUID id,
        UUID problemId,
        UUID matchId,
        Language language,
        String sourceCode,
        SubmissionStatus status,
        Verdict verdict,
        Integer testCasesPassed,
        Integer testCasesTotal,
        String testResults,
        Instant createdAt,
        Instant judgedAt
) {

    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(s.getId(), s.getProblemId(), s.getMatchId(), s.getLanguage(), s.getSourceCode(),
                s.getStatus(), s.getVerdict(), s.getTestCasesPassed(), s.getTestCasesTotal(),
                s.getTestResults(), s.getCreatedAt(), s.getJudgedAt());
    }
}
