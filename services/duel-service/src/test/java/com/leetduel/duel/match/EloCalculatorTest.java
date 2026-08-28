package com.leetduel.duel.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EloCalculatorTest {

    private final EloCalculator calculator = new EloCalculator(32);

    @Test
    void equalRatedWinUsesHalfKFactor() {
        assertThat(calculator.calculate(1500, 1500, 1.0))
                .isEqualTo(new EloCalculator.Result(16, -16));
    }

    @Test
    void equalRatedDrawDoesNotChangeEitherRating() {
        assertThat(calculator.calculate(1500, 1500, 0.5))
                .isEqualTo(new EloCalculator.Result(0, 0));
    }

    @Test
    void ratingDeltasRemainZeroSumForUnequalRatings() {
        EloCalculator.Result result = calculator.calculate(1600, 1400, 1.0);

        assertThat(result.player1Delta() + result.player2Delta()).isZero();
    }
}
