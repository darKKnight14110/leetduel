-- Sweeps a user out of the pool for exceeding max-wait, but re-checks pool
-- membership first (ZSCORE) so it can't stomp a MATCHED status a
-- concurrent pair_match.lua invocation just wrote for the same user in the
-- same sweep pass - same atomicity concern as leave_queue.lua, just
-- triggered by a timeout instead of a user action.
--
-- KEYS[1] = pool ZSET key
-- KEYS[2] = wait-start Hash key
-- KEYS[3] = this user's status Hash key
-- ARGV[1] = userId
-- ARGV[2] = expired-marker TTL, seconds (how long the EXPIRED status
--           survives so one more client poll can observe it before it
--           self-cleans)
--
-- Returns {1} if expired, {0} if the user was no longer in the pool
-- (already matched or already left).

local poolKey = KEYS[1]
local waitStartKey = KEYS[2]
local statusKey = KEYS[3]
local userId = ARGV[1]
local expiredTtlSeconds = ARGV[2]

if not redis.call('ZSCORE', poolKey, userId) then
    return {0}
end

redis.call('ZREM', poolKey, userId)
redis.call('HDEL', waitStartKey, userId)
redis.call('HSET', statusKey, 'state', 'EXPIRED')
redis.call('EXPIRE', statusKey, expiredTtlSeconds)

return {1}
