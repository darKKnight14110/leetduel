-- Cancels a waiting user, but detects the race where a concurrent pairing
-- pass (pair_match.lua) matched this exact user between the client's
-- cancel request and this script running - a naive "read status, then
-- ZREM" in Java would have exactly the race pair_match.lua exists to
-- avoid, just on this code path instead. Reports MATCHED instead of
-- pretending cancellation succeeded, so the client doesn't wrongly believe
-- it escaped a match that already happened.
--
-- KEYS[1] = pool ZSET key
-- KEYS[2] = wait-start Hash key
-- KEYS[3] = this user's status Hash key (fields: state, matchId)
-- ARGV[1] = userId
--
-- Returns {1} if the leave succeeded (pool/status cleared), or
-- {0, matchId} if the user was already matched before this ran.

local poolKey = KEYS[1]
local waitStartKey = KEYS[2]
local statusKey = KEYS[3]
local userId = ARGV[1]

local state = redis.call('HGET', statusKey, 'state')
if state == 'MATCHED' then
    return {0, redis.call('HGET', statusKey, 'matchId')}
end

redis.call('ZREM', poolKey, userId)
redis.call('HDEL', waitStartKey, userId)
redis.call('DEL', statusKey)

return {1}
