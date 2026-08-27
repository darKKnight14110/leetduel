-- Atomically finds the nearest still-waiting pool member to a candidate
-- within an ELO window, and if found, removes BOTH members from the pool
-- (and their wait-start bookkeeping) in the same atomic step.
--
-- Why one EVAL, not "search in Java, then ZREM in Java": this service runs
-- multiple instances (horizontal scaling - see docs/goals.md). Two
-- instances' scheduled sweeps can pick the same "oldest waiting" candidate
-- in the same moment, or both find the SAME third player as the nearest
-- opponent. If search-then-remove were two round trips, both instances
-- could double-book that third player into two different matches. Doing
-- the whole read-then-conditionally-mutate sequence inside one Lua script
-- makes it atomic - Redis executes one EVAL fully before starting the
-- next, so there is no window for a second script to observe a pool state
-- the first is still mutating. This is the exact same tool the API
-- Gateway's token_bucket.lua uses to close its own check-then-act race,
-- applied to a different problem.
--
-- Self-resolving freshness check, not a distributed lock: rather than
-- taking a lock on the candidate before searching, this script starts by
-- re-checking the candidate is STILL in the pool (ZSCORE). If another
-- instance's EVAL already matched/expired this candidate a moment earlier
-- (Redis serializes the two EVALs, so this can only ever be "already
-- happened," never "happening concurrently"), this script returns "not
-- present" and the caller treats that as a harmless no-op, moving on to
-- its next candidate. No ShedLock/leader-election is needed - correctness
-- comes from Redis's single-threaded EVAL execution model.
--
-- KEYS[1] = pool ZSET key (member=userId, score=elo)
-- KEYS[2] = wait-start Hash key (field=userId, value=join epoch millis)
-- ARGV[1] = candidateUserId
-- ARGV[2] = candidateElo (read by the caller just before this call)
-- ARGV[3] = minElo (candidateElo - current allowed window)
-- ARGV[4] = maxElo (candidateElo + current allowed window)
--
-- Returns {1, opponentUserId, opponentElo} if matched (both already
-- removed from KEYS[1]/KEYS[2]), or {0} if the candidate is stale or no
-- eligible opponent exists right now.

local poolKey = KEYS[1]
local waitStartKey = KEYS[2]
local candidateId = ARGV[1]
local candidateElo = tonumber(ARGV[2])
local minElo = tonumber(ARGV[3])
local maxElo = tonumber(ARGV[4])

local currentScore = redis.call('ZSCORE', poolKey, candidateId)
if not currentScore then
    return {0}
end

local inWindow = redis.call('ZRANGEBYSCORE', poolKey, minElo, maxElo, 'WITHSCORES')

local bestId, bestElo, bestDiff = nil, nil, nil
for i = 1, #inWindow, 2 do
    local memberId = inWindow[i]
    local memberElo = tonumber(inWindow[i + 1])
    if memberId ~= candidateId then
        local diff = math.abs(memberElo - candidateElo)
        if bestDiff == nil or diff < bestDiff then
            bestDiff, bestId, bestElo = diff, memberId, memberElo
        end
    end
end

if bestId == nil then
    return {0}
end

redis.call('ZREM', poolKey, candidateId, bestId)
redis.call('HDEL', waitStartKey, candidateId, bestId)

return {1, bestId, tostring(bestElo)}
