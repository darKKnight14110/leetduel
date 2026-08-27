package com.leetduel.user.profile;

import com.leetduel.user.dto.InternalProfileDto;
import com.leetduel.user.event.MatchCompletedEvent;
import com.leetduel.user.exception.ProfileNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

// First service layer in user-service - added ahead of need at Phase 0/1
// specifically anticipating this method: the read-modify-write ELO delta
// application match.completed needs.
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final ProcessedMatchCompletionRepository processedMatchCompletionRepository;
    private final EloHistoryRepository eloHistoryRepository;

    public InternalProfileDto getProfile(UUID userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException("No profile yet for user " + userId));
        return new InternalProfileDto(profile.getUserId(), profile.getElo());
    }

    public UserProfile getProfileOrThrow(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException("No profile yet for user " + userId));
    }

    public List<EloHistoryEntry> getEloHistory(UUID userId) {
        return eloHistoryRepository.findTop200ByUserIdOrderByRecordedAtAsc(userId);
    }

    public List<UserProfile> getProfiles(Collection<UUID> userIds) {
        return userProfileRepository.findAllById(userIds);
    }

    // Sole writer of ELO (per docs/goals.md) - Duel Service computes the
    // delta but never writes into this schema directly. Single transaction:
    // the dedup-row insert, both profile updates, and both elo_history rows
    // commit together or not at all, so a crash mid-apply can never leave
    // the dedup marker present without every other write having landed (or
    // vice versa).
    @Transactional
    public void applyMatchResult(MatchCompletedEvent event) {
        if (processedMatchCompletionRepository.existsById(event.matchId())) {
            log.debug("Match {} already applied, skipping (duplicate delivery)", event.matchId());
            return;
        }

        applyResultToPlayer(event.matchId(), event.player1Id(), event.player1EloDelta(), event.player2EloAtMatch(),
                event.isDraw(), event.winnerId() != null && event.winnerId().equals(event.player1Id()));
        applyResultToPlayer(event.matchId(), event.player2Id(), event.player2EloDelta(), event.player1EloAtMatch(),
                event.isDraw(), event.winnerId() != null && event.winnerId().equals(event.player2Id()));

        processedMatchCompletionRepository.save(new ProcessedMatchCompletion(event.matchId()));
    }

    // opponentEloAtMatch is deliberately the FROZEN value from the event,
    // never the opponent's current live ELO - see docs/goals.md's "opponent's
    // ELO-at-match-time, not their live post-match ELO" note: using live ELO
    // would make sum_opp_elo_* depend on when it's read, not what actually
    // happened in that match.
    private void applyResultToPlayer(UUID matchId, UUID playerId, int eloDelta, int opponentEloAtMatch,
            boolean isDraw, boolean won) {
        UserProfile profile = userProfileRepository.findById(playerId)
                .orElseThrow(() -> new ProfileNotFoundException("No profile yet for user " + playerId));

        profile.setElo(profile.getElo() + eloDelta);
        if (isDraw) {
            profile.setDuelsDrawn(profile.getDuelsDrawn() + 1);
            profile.setSumOppEloDrawn(profile.getSumOppEloDrawn() + opponentEloAtMatch);
        } else if (won) {
            profile.setDuelsWon(profile.getDuelsWon() + 1);
            profile.setSumOppEloWon(profile.getSumOppEloWon() + opponentEloAtMatch);
        } else {
            profile.setDuelsLost(profile.getDuelsLost() + 1);
            profile.setSumOppEloLost(profile.getSumOppEloLost() + opponentEloAtMatch);
        }
        userProfileRepository.save(profile);
        eloHistoryRepository.save(new EloHistoryEntry(playerId, matchId, profile.getElo(), eloDelta));
    }
}
