package com.leetduel.duel.match;

import com.leetduel.duel.event.DuelProgressEvent;
import com.leetduel.duel.event.MatchCompletedEvent;
import com.leetduel.duel.event.MatchCreatedEvent;
import com.leetduel.duel.event.SubmissionJudgedEvent;
import com.leetduel.duel.outbox.OutboxEvent;
import com.leetduel.duel.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

// Owns every write to duel.matches. Both entry points below are called from
// @RabbitListener consumers (MatchCreatedListener, SubmissionJudgedListener)
// and MatchTimeoutSweeper - each method is its own @Transactional boundary,
// same self-invocation reasoning as submission-service's SubmissionWriter
// (this bean is never called via "this." from within itself).
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EloCalculator eloCalculator;
    private final ObjectMapper objectMapper;

    @Transactional
    public void createMatch(MatchCreatedEvent event) {
        if (matchRepository.existsById(event.matchId())) {
            // At-least-once redelivery of match.created - matchmaking-service's
            // outbox relay retries until it sees a publish succeed, so this
            // consumer may see the same event twice. Same idempotency shape
            // as SubmissionJudgedListener's status-check guard.
            log.debug("Match {} already exists, skipping (duplicate delivery)", event.matchId());
            return;
        }

        Match match = new Match();
        match.setId(event.matchId());
        match.setPlayer1Id(event.userAId());
        match.setPlayer2Id(event.userBId());
        match.setPlayer1EloAtMatch(event.userAEloAtMatch());
        match.setPlayer2EloAtMatch(event.userBEloAtMatch());
        match.setProblemId(event.problemId());
        match.setTimeLimitMs(event.timeLimitMs());
        match.setStartedAt(Instant.now());
        matchRepository.save(match);
    }

    // Caller (SubmissionJudgedListener) is responsible for catching
    // ObjectOptimisticLockingFailureException and treating it as a benign
    // no-op - see Match.version's comment.
    @Transactional
    public void applyProgress(SubmissionJudgedEvent event) {
        Match match = matchRepository.findById(event.matchId()).orElse(null);
        if (match == null) {
            log.warn("Received submission.judged for unknown match {}", event.matchId());
            return;
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            // Match already decided (win or timeout) - a late-arriving
            // verdict for a submission made before the match closed must
            // not reopen or re-score it.
            log.debug("Match {} no longer in progress, ignoring late verdict", event.matchId());
            return;
        }

        boolean isPlayer1 = match.isPlayer1(event.userId());
        if (!isPlayer1 && !match.getPlayer2Id().equals(event.userId())) {
            log.warn("Submission {} for match {} belongs to neither player", event.submissionId(), event.matchId());
            return;
        }

        int newProgress = event.testCasesTotal() == 0 ? 0
                : (int) Math.round(100.0 * event.testCasesPassed() / event.testCasesTotal());

        if (isPlayer1) {
            match.setPlayer1ProgressPct(Math.max(match.getPlayer1ProgressPct(), newProgress));
        } else {
            match.setPlayer2ProgressPct(Math.max(match.getPlayer2ProgressPct(), newProgress));
        }
        matchRepository.save(match);
        publishProgress(match.getId(), event.userId(),
                isPlayer1 ? match.getPlayer1ProgressPct() : match.getPlayer2ProgressPct());

        if (isPlayer1 && match.getPlayer1ProgressPct() == 100) {
            complete(match, match.getPlayer1Id(), false);
        } else if (!isPlayer1 && match.getPlayer2ProgressPct() == 100) {
            complete(match, match.getPlayer2Id(), false);
        }
    }

    // Caller (MatchTimeoutSweeper) is responsible for catching
    // ObjectOptimisticLockingFailureException - see applyProgress's comment.
    @Transactional
    public void completeOnTimeout(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null || match.getStatus() != MatchStatus.IN_PROGRESS) {
            return;
        }

        if (match.getPlayer1ProgressPct() > match.getPlayer2ProgressPct()) {
            complete(match, match.getPlayer1Id(), false);
        } else if (match.getPlayer2ProgressPct() > match.getPlayer1ProgressPct()) {
            complete(match, match.getPlayer2Id(), false);
        } else {
            complete(match, null, true);
        }
    }

    private void complete(Match match, UUID winnerId, boolean isDraw) {
        double player1Score = isDraw ? 0.5 : (match.getPlayer1Id().equals(winnerId) ? 1.0 : 0.0);
        EloCalculator.Result delta = eloCalculator.calculate(
                match.getPlayer1EloAtMatch(), match.getPlayer2EloAtMatch(), player1Score);

        match.setStatus(MatchStatus.COMPLETED);
        match.setWinnerId(winnerId);
        match.setDraw(isDraw);
        match.setPlayer1EloDelta(delta.player1Delta());
        match.setPlayer2EloDelta(delta.player2Delta());
        match.setEndedAt(Instant.now());
        matchRepository.save(match);

        MatchCompletedEvent event = new MatchCompletedEvent(
                match.getId(), match.getPlayer1Id(), match.getPlayer2Id(), winnerId, isDraw,
                match.getPlayer1EloAtMatch(), match.getPlayer2EloAtMatch(),
                delta.player1Delta(), delta.player2Delta());
        writeOutbox("match.completed", event);
    }

    private void publishProgress(UUID matchId, UUID userId, int progressPct) {
        writeOutbox("duel.progress", new DuelProgressEvent(matchId, userId, progressPct));
    }

    private void writeOutbox(String eventType, Object payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
        outboxEventRepository.save(outboxEvent);
    }
}
