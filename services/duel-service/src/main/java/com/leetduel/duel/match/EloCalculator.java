package com.leetduel.duel.match;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Standard logistic-curve ELO, same formula chess.com/FIDE use. Both deltas
// are computed from ELO-AT-MATCH-TIME (frozen on the Match row), never a
// live rating - a delta computed against a rating that already moved
// earlier the same tick would double-count that movement.
@Component
public class EloCalculator {

    private final int kFactor;

    public EloCalculator(@Value("${leetduel.duel.elo.k-factor}") int kFactor) {
        this.kFactor = kFactor;
    }

    public record Result(int player1Delta, int player2Delta) {
    }

    // score is from player1's perspective: 1.0 win, 0.5 draw, 0.0 loss.
    public Result calculate(int player1Elo, int player2Elo, double player1Score) {
        double expectedPlayer1 = 1.0 / (1.0 + Math.pow(10, (player2Elo - player1Elo) / 400.0));
        double player1Delta = kFactor * (player1Score - expectedPlayer1);
        // Zero-sum by construction - player2's delta is exactly the
        // negation, since expectedPlayer2 = 1 - expectedPlayer1 and
        // player2Score = 1 - player1Score algebraically collapse to this.
        return new Result((int) Math.round(player1Delta), (int) Math.round(-player1Delta));
    }
}
