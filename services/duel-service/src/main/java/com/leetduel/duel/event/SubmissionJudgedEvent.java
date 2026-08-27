package com.leetduel.duel.event;

import java.util.List;
import java.util.UUID;

// Consumer-side duplicate of judge-worker's producer record of the same
// name (submission-service maintains its own independent copy too - see
// its event package). Mirrors the FULL payload even though this service
// only reads matchId/verdict/testCasesPassed/testCasesTotal, matching this
// repo's convention of independently mirroring the whole wire shape rather
// than a partial subset.
public record SubmissionJudgedEvent(
        UUID submissionId,
        // Null for practice-mode submissions - filtered out by
        // SubmissionJudgedListener before this reaches MatchService.
        UUID matchId,
        // WHICH player submitted - matched against Match.player1Id/player2Id
        // to decide whose progress column to update.
        UUID userId,
        String verdict,
        int testCasesPassed,
        int testCasesTotal,
        List<TestCaseResultPayload> testResults) {

    public record TestCaseResultPayload(
            int ordinal,
            String status,
            Long runtimeMs,
            String expectedOutput,
            String actualOutput) {
    }
}
