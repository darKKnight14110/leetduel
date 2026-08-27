package com.leetduel.matchmaking.match;

import com.leetduel.matchmaking.event.MatchCreatedEvent;
import com.leetduel.matchmaking.outbox.OutboxEvent;
import com.leetduel.matchmaking.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

// Public (unlike submission-service's package-private SubmissionWriter) -
// this service's caller (MatchmakingSweepScheduler) lives in a different
// package (queue/, not match/), since matchmaking-service genuinely has
// two domain nouns rather than one. Split out as its own bean specifically
// so the @Transactional boundary below never wraps a network call - same
// self-invocation reasoning as SubmissionWriter. Callers must resolve the
// problem ID via ProblemServiceClient BEFORE calling persist(), never from
// inside it.
@Component
@RequiredArgsConstructor
public class MatchWriter {

    private final MatchRepository matchRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UUID persist(UUID userAId, int userAElo, UUID userBId, int userBElo, UUID problemId, int timeLimitMs) {
        Match match = new Match();
        match.setUserAId(userAId);
        match.setUserBId(userBId);
        match.setUserAEloAtMatch(userAElo);
        match.setUserBEloAtMatch(userBElo);
        match.setProblemId(problemId);
        match.setTimeLimitMs(timeLimitMs);
        match = matchRepository.save(match);

        MatchCreatedEvent event = new MatchCreatedEvent(
                match.getId(), userAId, userBId, userAElo, userBElo, problemId, timeLimitMs);
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setEventType("match.created");
        outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        outboxEventRepository.save(outboxEvent);

        return match.getId();
    }
}
