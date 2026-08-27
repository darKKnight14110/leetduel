package com.leetduel.leaderboard.period;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;

// Pure, Spring-free utility - deliberately no dependency on this service's
// runtime so it's trivially unit-testable in isolation (see
// PeriodKeyResolverTest). Period identity lives entirely in the derived
// key string; rolling into a new week/quarter is just "start writing to a
// new key name," no scheduled reset job needed (see
// increment_period_score.lua's EXPIRE ... NX comment for the other half of
// that story).
//
// UTC, not server-local time zone: this service may run as multiple
// horizontally-scaled instances (same reasoning as matchmaking-service's
// pool sweep), and a period boundary must be identical across all of them
// regardless of which host or time zone they happen to run in.
public final class PeriodKeyResolver {

    // WeekFields.ISO's weekBasedYear() is deliberately used instead of
    // Year.from(instant) - the classic off-by-one trap here is that the
    // last few days of December can belong to week 1 of the FOLLOWING
    // calendar year under ISO-8601 (e.g. 2025-12-29 falls in 2026-W01), and
    // Year.from would silently mislabel that key. See
    // PeriodKeyResolverTest's boundary cases.
    public static String isoWeekKey(Instant instant) {
        ZonedDateTime dt = instant.atZone(ZoneOffset.UTC);
        int weekBasedYear = dt.get(WeekFields.ISO.weekBasedYear());
        int week = dt.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%04d-W%02d", weekBasedYear, week);
    }

    public static String quarterKey(Instant instant) {
        ZonedDateTime dt = instant.atZone(ZoneOffset.UTC);
        int quarter = dt.get(IsoFields.QUARTER_OF_YEAR);
        return String.format("%04d-Q%d", dt.getYear(), quarter);
    }

    private PeriodKeyResolver() {
    }
}
