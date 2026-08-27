# LeetDuel — System Design Document

Target: backend-heavy systems project demonstrating full system-design-primer coverage for top systems/backend engineering roles.

Status: Phase 0 (foundations) and Phase 1 (core judge loop) are implemented and demoed end-to-end. Phase 2 (ELO + matchmaking) is next. See "Implementation phases" below for the full breakdown.

## Pitch

LeetCode-style problem practice platform + real-time 1v1 ranked duels (ELO-matched), built on microservices to deliberately exercise every major system design primer component: async messaging, caching, service-to-service communication, real-time client-server-client push, container orchestration, and observability.

## Scope decision

Full platform, built in phases. Each phase independently demoable and resume-worthy (not a big-bang build).

## Services

| Service | Responsibility | Store |
|---|---|---|
| API Gateway | Spring Cloud Gateway, JWT validation, routing, token-bucket rate limiting (atomic Redis Lua script — see "Auth / Gateway" below) | — |
| Auth Service | signup/login, JWT issuance (OAuth2/Spring Security) | Postgres |
| User/Profile Service | profile, ELO + external ratings + duel stats; consumes `match.completed` to apply ELO delta and update W/L/D counters (sole writer of ELO) | Postgres |
| Problem Service | problem CRUD, test cases, tags/difficulty | Postgres |
| Submission Service | accepts code, publishes judge job | Postgres (metadata) |
| Judge Worker (pool) | consumes RabbitMQ job, spins Docker sandbox, runs code vs test cases, scores, emits result | MongoDB (per-test-case output/logs, code snapshot) |
| Matchmaking Service | RabbitMQ join queue in, Redis sorted-set expanding-window ELO pairing | Redis (matching index) |
| Duel/Match Service | owns live match lifecycle, both players' progress, decides winner, computes ELO delta (has both ratings from match creation, no cross-service lookup needed) | Postgres (match record) |
| WS Gateway Service | holds client WebSocket connections, fanout via Redis pub/sub, routes opponent progress via Redis `matchId -> connectionIds` | Redis (pub/sub, session map) |
| Leaderboard Service | consumes match-completed events off RabbitMQ, maintains ranked leaderboard | Redis (sorted set) |

Gateway, Auth, User/Profile, Problem, Submission, and Judge Worker are implemented (Phases 0-1). Matchmaking, Duel/Match, WS Gateway, and Leaderboard are designed but not yet built (Phase 2+).

## Async backbone (Kafka dropped as overkill for this project's actual scale)

- **RabbitMQ** — does everything async: judge job queue (task-queue semantics, worker pool consumes), matchmaking join-request queue, and match event fanout via a topic exchange (multiple bound queues: Duel Service, WS Gateway, and Leaderboard Service each get `match.created` / `match.completed` / `duel.progress`; User/Profile Service binds only `match.completed`, to apply the ELO delta and duel counters).
- **Redis** — matching index (sorted set by ELO), leaderboard (sorted set), WS session/connection registry + pub/sub for cross-instance fanout, general cache (hot problem statements, user profiles), and the API Gateway's rate-limit token buckets (implemented).
- Explicit trade-off accepted: no Kafka means no durable event replay / long retention / event-sourcing story. Fine at this scale — the two remaining tools (RabbitMQ, Redis) each still have a genuine, explainable reason for being there, which was the actual goal (demonstrating *when/why* to reach for a tool, not raw scale).
- **Transactional outbox for DB-triggered publishes (implemented).** Auth Service's signup writes an `auth.outbox_events` row in the *same* transaction as the `auth.users` insert, rather than publishing to RabbitMQ from an `AFTER_COMMIT` listener. A separate poller (`OutboxRelay`, ~2s interval) relays unpublished rows and marks them sent. This closes the crash window an `AFTER_COMMIT` listener leaves open — a process crash between commit and listener execution used to lose the event permanently; now it's just an unpublished row waiting for the next poll, nothing is ever silently dropped. Trade-off: polling adds a few seconds of latency and repeated (cheap, indexed) queries versus CDC/Debezium reading the DB's write-ahead log directly (near-zero latency, no poll cost) — but CDC means running Kafka Connect or a Debezium server, reintroducing the Kafka-adjacent infra this project already decided against. Revisit if latency ever actually matters here; it doesn't yet. Any future service writing to a queue on the back of a DB transaction should use the same pattern, not the `AFTER_COMMIT` shortcut.

## Data strategy

Polyglot, database-per-service where it matters:
- **Postgres** — core relational/transactional entities: users, problems + test cases, matches, ELO history. ACID needed.
- **MongoDB** — high-write, variable-shape submission execution results (code snapshot, per-test-case output/logs).
- **Redis** — ephemeral/hot state only (matching queue, leaderboard, WS sessions, cache, rate-limit buckets). Not source of truth for anything durable.

## Code execution / judging (implemented)

Docker sandbox workers: isolated containers per submission, resource-limited (CPU/mem/time), pulled from RabbitMQ job queue by a worker pool. Real isolation/security story (container escape prevention, resource limits, timeout kill) — not a subprocess/ulimit shortcut. Sandbox images run non-root, network-disabled, read-only root filesystem; Python 3.12 and Java 21 (JDK) runtimes exist today (`docker/sandbox-python`, `docker/sandbox-java`).

## Real-time transport (planned, Phase 3)

WebSocket via a dedicated WS Gateway service. Connections registered in Redis so gateway can run multi-instance (critical: without the Redis `matchId -> connectionIds` lookup, horizontal scaling breaks opponent-push routing — this is the actual reason Redis pub/sub is used instead of plain in-memory WS state).

## Matchmaking algorithm (planned, Phase 2)

Expanding-window ELO matching (like chess.com/League):
1. Client joins queue → publishes `join_request{userId, elo}` to RabbitMQ `matchmaking.join` queue (durable, at-least-once, survives matchmaker restarts).
2. Matchmaker Service consumes, inserts into Redis sorted set keyed by ELO.
3. On each insert, scans sorted set for a pair within current acceptable rating gap; gap widens the longer either user has waited (bounds wait time while preserving fairness early on).
4. Match found → both removed from Redis set, Match record created, `match.created` published to RabbitMQ topic exchange (fans out to Duel Service + WS Gateway).

## Live duel flow (planned, Phase 3)

1. Match found → both clients pushed `match.created` over WS (problem ID, opponent username/ELO, time limit e.g. 20 min).
2. Players code independently (React/Monaco editor), submit via normal Submission Service flow, submissions tagged `matchId`.
3. Judge Worker scores → result to RabbitMQ → Duel Service updates that player's progress % in match record → republishes `duel.progress` on topic exchange.
4. WS Gateway consumes `duel.progress`, looks up opponent's connection via Redis, pushes **progress bar only** (no code, no submission counts — preserves competitive integrity, keeps payload simple).
5. **Win condition:** first to pass 100% test cases wins immediately, match closes to further scoring. If time limit expires with no 100%, higher progress % wins; exact tie = draw.
6. On completion: Duel Service computes ELO delta (standard ELO formula, K-factor ~32) using each player's rating captured at match creation, updates its own match record, publishes `match.completed` (winner/loser/draw, both ELO deltas, both players' ELO-at-match-time) → User/Profile Service applies the delta and duel counters to its own row (sole writer of ELO - Duel Service never writes into Profile's table) → Leaderboard Service updates Redis sorted set → WS Gateway pushes final result to both clients.
   - Opponent's ELO-at-match-time (not their live post-match ELO) is what Profile Service sums into `sum_opp_elo_won/lost/drawn` - using live ELO would make the aggregate depend on when it's read, not what actually happened in that match.

## Deployment (planned, Phase 6)

Kubernetes. Real manifests/Helm charts, k8s-native service discovery (no separate Eureka layer — redundant once on k8s), HPA autoscaling, rolling deploys. Local dev via kind/minikube or Docker Desktop's k8s, portable to any cloud. Local pre-k8s dev runs on `docker-compose.infra.yml` (implemented).

## Auth / Gateway (implemented)

Spring Cloud Gateway in front of everything: JWT validation, routing, rate limiting. Separate Auth Service issues JWTs (email/password + Google OAuth2/Spring Security, refresh-token rotation). Keeps whole stack in Spring ecosystem.

Rate limiting is a token bucket, not a fixed window: bucket state (tokens + last-refill timestamp) lives in one Redis Hash key per client, refilled/consumed by a single atomic Lua `EVAL` (`scripts/token_bucket.lua`). Single-key-per-client makes this safe on a real Redis Cluster with no code changes (no CROSSSLOT risk from multi-key scripts), and the atomic EVAL removes the check-then-act race a plain GET/SET or INCR/EXPIRE pair would have under concurrent requests.

## Observability (v1 scope, planned Phase 7)

Prometheus + Grafana only for v1 (Spring Boot Actuator + Micrometer exposing `/actuator/prometheus`, scraped via kube-prometheus-stack). Distributed tracing (OpenTelemetry/Jaeger), centralized logging (ELK/Loki), and Resilience4j circuit breakers/rate limiting are explicitly deferred to a later phase — not v1 scope, called out here so it isn't forgotten, but v1 should not creep into building them.

## Frontend

Full React + TypeScript SPA: Monaco code editor, problem browser, matchmaking/queue screen, live duel view (WS-driven opponent progress bar), leaderboard, profile/stats/rating history. Landing page, login/signup (wired to Auth Service), and the problem browser are implemented; matchmaking/duel/leaderboard views are planned alongside their backend phases.

## Open questions / deferred decisions

- Exact data model / schema for the not-yet-built services (Matchmaking, Duel/Match, Leaderboard)
- Judge sandbox anti-cheat considerations (plagiarism detection is explicitly out of v1 scope unless revisited)
- Testing strategy per service (unit/integration/e2e, contract tests between services)
- CI/CD pipeline
- Exact k8s manifest/Helm chart layout
- Stale/expired matchmaking join request handling (max-wait/expiry) — raised, not yet resolved

These get resolved incrementally as each phase is implemented, not up front.

## Implementation phases

0. **Foundations — done.** Repo structure, `docker-compose.infra.yml` (Postgres, Mongo, Redis, RabbitMQ), Auth Service, API Gateway, User Service — login working end-to-end.
1. **Core judge loop — done.** Problem Service, Submission Service, Judge Worker (Docker sandbox), RabbitMQ job queue — single-player practice mode fully working.
2. **ELO + Matchmaking — next.** Rating model, Matchmaking Service, Redis expanding-window pairing.
3. Real-time duel: WS Gateway, Redis pub/sub fanout, live duel state, ELO update on completion.
4. Leaderboard + profile/stats.
5. Frontend React SPA (built incrementally alongside each phase's API surface rather than as one final phase).
6. Kubernetes deployment: Helm charts, HPA, migrate from docker-compose to k8s.
7. Observability: Prometheus + Grafana dashboards (service health, queue depth, judge latency, match wait time, per-service request rate/latency/error rate).
