package com.leetduel.matchmaking.config;

import java.util.UUID;

// Hash-tagged pool/wait-start keys ({pool}) so both land on the same Redis
// Cluster slot - pair_match.lua genuinely touches two keys in one EVAL
// (unlike the Gateway's token_bucket.lua, which only ever needs one), and
// a multi-key script errors with CROSSSLOT on a real cluster unless every
// key it touches hashes to the same slot.
public final class RedisKeys {

    public static final String POOL = "matchmaking:{pool}";
    public static final String WAIT_START = "matchmaking:{pool}:wait_start";

    public static String status(UUID userId) {
        return "matchmaking:status:" + userId;
    }

    private RedisKeys() {
    }
}
