package com.leetduel.leaderboard.period;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Table-driven over fixed Instants - the classic off-by-one traps in
// date-bucketing code are exactly the ISO week-year boundary (the last few
// days of December can belong to week 1 of the FOLLOWING calendar year)
// and the calendar-quarter boundary. See PeriodKeyResolver's own comment
// on why weekBasedYear(), not Year.from(instant), is required here.
class PeriodKeyResolverTest {

    @ParameterizedTest
    @CsvSource({
            // 2025-12-29 (Mon) starts ISO week 1 of 2026, even though the
            // calendar year is still 2025 - the exact trap Year.from would fall into.
            "2025-12-29T00:00:00Z, 2026-W01",
            "2025-12-28T23:59:59Z, 2025-W52",
            "2026-01-01T00:00:00Z, 2026-W01",
            "2026-08-24T12:00:00Z, 2026-W35",
    })
    void isoWeekKey_handlesYearBoundaryCorrectly(String instant, String expectedKey) {
        assertThat(PeriodKeyResolver.isoWeekKey(Instant.parse(instant))).isEqualTo(expectedKey);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-01T00:00:00Z, 2026-Q1",
            "2026-03-31T23:59:59Z, 2026-Q1",
            "2026-04-01T00:00:00Z, 2026-Q2",
            "2026-06-30T23:59:59Z, 2026-Q2",
            "2026-07-01T00:00:00Z, 2026-Q3",
            "2026-09-30T23:59:59Z, 2026-Q3",
            "2026-10-01T00:00:00Z, 2026-Q4",
            "2026-12-31T23:59:59Z, 2026-Q4",
    })
    void quarterKey_handlesQuarterBoundariesCorrectly(String instant, String expectedKey) {
        assertThat(PeriodKeyResolver.quarterKey(Instant.parse(instant))).isEqualTo(expectedKey);
    }
}
