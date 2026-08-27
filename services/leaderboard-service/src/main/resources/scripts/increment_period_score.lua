-- Atomically applies an additive ELO delta to a period-scoped leaderboard
-- (weekly/season) exactly once per (matchId, userId, period), even under
-- RabbitMQ's at-least-once redelivery. Unlike the global board (plain ZADD
-- with an absolute post-match ELO value - naturally idempotent, replaying
-- the same event just sets the same score twice), ZINCRBY is NOT
-- idempotent: replaying the same delta would double-count that match's
-- ELO swing. Same "check-then-act must be one EVAL" shape as
-- matchmaking-service's pair_match.lua and the Gateway's token_bucket.lua,
-- applied here to dedup-then-increment instead of search-then-remove or
-- refill-then-consume.
--
-- KEYS[1] = period ZSET key (e.g. leaderboard:weekly:2026-W35)
-- KEYS[2] = idempotency marker key (e.g. leaderboard:applied:2026-W35:<matchId>:<userId>)
-- ARGV[1] = userId (ZSET member)
-- ARGV[2] = eloDelta (may be negative)
-- ARGV[3] = idempotency marker TTL, seconds
-- ARGV[4] = period ZSET TTL, seconds
--
-- Returns {1} if applied, {0} if already applied (harmless no-op - the
-- caller treats this exactly like user-service's own
-- ProcessedMatchCompletion short-circuit).

local periodKey = KEYS[1]
local markerKey = KEYS[2]
local userId = ARGV[1]
local delta = tonumber(ARGV[2])
local markerTtl = ARGV[3]
local periodTtl = ARGV[4]

if redis.call('EXISTS', markerKey) == 1 then
    return {0}
end

-- Marker set BEFORE the increment: a crash between the two calls drops a
-- delta rather than double-applying it - under-counting a best-effort
-- weekly/season board is acceptable, double-crediting ELO is not.
redis.call('SET', markerKey, '1', 'EX', markerTtl)
redis.call('ZINCRBY', periodKey, delta, userId)

-- NX: only the FIRST writer to this period key ever sets its expiry - this
-- is the entire rollover mechanism (a new week/quarter just means writing
-- to a new key name). Without NX, every subsequent match landing in the
-- same period would keep pushing the TTL back out, so the key would never
-- actually expire.
redis.call('EXPIRE', periodKey, periodTtl, 'NX')

return {1}
