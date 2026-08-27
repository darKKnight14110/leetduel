-- Token bucket rate limiter, single atomic EVAL.
--
-- Why Lua instead of INCR+EXPIRE (the old fixed-window approach): the
-- refill/consume step is read-modify-write (read current tokens, compute
-- refill, maybe subtract, write back). Two round-trips from the app server
-- for that (GET then SET) race under concurrency - two requests can both
-- read tokens=1, both decide "allowed", both write tokens=0, and 2 requests
-- get let through for the cost of 1. Redis executes a single EVAL as one
-- atomic step on its event loop; no other client's command can interleave
-- mid-script. This is the standard token-bucket-on-Redis pattern (same one
-- Stripe and AWS API Gateway docs describe) precisely because it removes
-- that race without needing a client-side lock.
--
-- Why a single key (Redis Hash, not two separate keys): Redis Cluster
-- shards keys by CRC16(key) mod 16384 into slots, and a script touching
-- multiple keys errors (CROSSSLOT) unless every key hashes to the same
-- slot. Keeping tokens+timestamp as two fields of ONE hash key means this
-- script is cluster-safe by construction - no hash tags needed, and it'll
-- keep working unchanged the day this Redis becomes an actual cluster
-- instead of the single dev instance it is today.
--
-- Why redis.call('TIME') instead of a timestamp passed in from the app:
-- this Gateway runs multiple instances behind a load balancer (see
-- goals.md's HPA note). If each instance used its own wall clock, refill
-- math would depend on which instance's clock is fast/slow, and instances
-- can drift relative to each other. Pulling time from Redis gives every
-- instance the same clock for this calculation, so the bucket behaves
-- identically no matter which gateway pod handled the request. (Since
-- Redis 7, scripts replicate their *effects* rather than the command
-- itself, so calling a nondeterministic command like TIME inside a script
-- is safe - replicas end up with the same HSET/EXPIRE effects, not a
-- second independent TIME call.)
--
-- KEYS[1] = bucket key (e.g. "ratelimit:token-bucket:203.0.113.7")
-- ARGV[1] = capacity (max tokens a bucket can hold - the burst allowance)
-- ARGV[2] = refill_rate (tokens added per second - the steady-state rate)
-- ARGV[3] = requested (tokens this request costs, normally 1)
-- ARGV[4] = ttl_seconds (expiry so an idle client's key doesn't live forever)
--
-- Returns {allowed (0 or 1), tokens_remaining}

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local ttl_seconds = tonumber(ARGV[4])

local time_parts = redis.call('TIME')
local now = tonumber(time_parts[1]) + (tonumber(time_parts[2]) / 1000000)

local bucket = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Missing key = a bucket that's never been touched, or one that expired
-- because it sat idle past its full-refill time. Either way, "start full"
-- is correct: a bucket idle long enough to expire has, by definition,
-- already earned back every token it could hold.
if tokens == nil then
    tokens = capacity
    last_refill = now
end

local elapsed = math.max(0, now - last_refill)
tokens = math.min(capacity, tokens + (elapsed * refill_rate))

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', key, 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', key, ttl_seconds)

return {allowed, tokens}
