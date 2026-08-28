# LeetDuel — System Design Document

Target: backend-heavy systems project demonstrating full system-design-primer coverage for top systems/backend engineering roles.

Status: Phases 0-5 and Phase 6 (Minikube/Helm deployment) are implemented. See "Implementation phases" below for the full breakdown.

## Pitch

LeetCode-style problem practice platform + real-time 1v1 ranked duels (ELO-matched), built on microservices to deliberately exercise every major system design primer component: async messaging, caching, service-to-service communication, real-time client-server-client push, container orchestration, and observability.

## Scope decision

Full platform, built in phases. Each phase independently demoable and resume-worthy (not a big-bang build).

## Services

| Service | Responsibility | Store |
|---|---|---|
| API Gateway | Spring Cloud Gateway, JWT validation, routing, token-bucket rate limiting (atomic Redis Lua script — see "Auth / Gateway" below) | — |
| Auth Service | signup/login, JWT issuance (Spring Security) | Postgres |
| User/Profile Service | profile, public username projection, ELO + external ratings + match stats; consumes `match.completed` to apply ELO delta and update W/L/D counters (sole writer of ELO) | Postgres |
| Problem Service | problem CRUD, test cases, tags/difficulty | Postgres |
| Submission Service | accepts code, publishes judge job | Postgres (metadata) |
| Judge Dispatcher / Executor | Dispatcher consumes RabbitMQ jobs and owns Kubernetes Job lifecycle; one non-networked Executor Job runs code and emits a framed result | Dispatcher is stateless; result is persisted by Submission Service |
| Matchmaking Service | RabbitMQ join queue in, Redis sorted-set expanding-window ELO pairing | Redis (matching index) |
| Duel Service | owns live match lifecycle, both players' progress, decides winner, computes ELO delta (has both ratings from match creation, no cross-service lookup needed) | Postgres (match record) |
| WS Gateway Service | holds client WebSocket connections, fanout via Redis pub/sub, routes opponent progress via a topic-per-match STOMP subscription | Redis (pub/sub relay channel) |
| Leaderboard Service | consumes match-completed events off RabbitMQ, maintains ranked leaderboard | Redis (sorted set) |

Gateway, Auth, User/Profile, Problem, Submission, Judge Dispatcher/Executor, Matchmaking, Duel Service, WS Gateway, and Leaderboard are implemented (Phases 0-6).

## Async backbone (Kafka dropped as overkill for this project's actual scale)

- **RabbitMQ** — does everything async: judge job queue (task-queue semantics, worker pool consumes), matchmaking join-request queue, and match event fanout via a topic exchange (multiple bound queues: Duel Service, WS Gateway, and Leaderboard Service each get `match.created` / `match.completed` / `duel.progress`; User/Profile Service binds only `match.completed`, to apply the ELO delta and duel counters).
- **Redis** — matching index (sorted set by ELO), leaderboard (sorted set), WS session/connection registry + pub/sub for cross-instance fanout, general cache (hot problem statements, user profiles), and the API Gateway's rate-limit token buckets (implemented).
- Explicit trade-off accepted: no Kafka means no durable event replay / long retention / event-sourcing story. Fine at this scale — the two remaining tools (RabbitMQ, Redis) each still have a genuine, explainable reason for being there, which was the actual goal (demonstrating *when/why* to reach for a tool, not raw scale).
- **Transactional outbox for DB-triggered publishes (implemented).** Auth Service's signup writes an `auth.outbox_events` row in the *same* transaction as the `auth.users` insert, rather than publishing to RabbitMQ from an `AFTER_COMMIT` listener. A separate poller (`OutboxRelay`, ~2s interval) relays unpublished rows and marks them sent. This closes the crash window an `AFTER_COMMIT` listener leaves open — a process crash between commit and listener execution used to lose the event permanently; now it's just an unpublished row waiting for the next poll, nothing is ever silently dropped. Trade-off: polling adds a few seconds of latency and repeated (cheap, indexed) queries versus CDC/Debezium reading the DB's write-ahead log directly (near-zero latency, no poll cost) — but CDC means running Kafka Connect or a Debezium server, reintroducing the Kafka-adjacent infra this project already decided against. Revisit if latency ever actually matters here; it doesn't yet. Any future service writing to a queue on the back of a DB transaction should use the same pattern, not the `AFTER_COMMIT` shortcut.

## Data strategy

Polyglot, database-per-service where it matters:
- **Postgres** — core relational/transactional entities: users, problems + test cases, matches, ELO history. ACID needed.
- **Postgres JSONB** — variable-shape submission execution results (per-test-case output/logs) stored with Submission Service's relational submission record. MongoDB was evaluated and dropped to avoid a second durable database for data that already has a stable relational owner.
- **Redis** — ephemeral/hot state only (matching queue, leaderboard, WS sessions, cache, rate-limit buckets). Not source of truth for anything durable.

## Code execution / judging (implemented)

The local Compose profile keeps isolated Docker sandbox workers per submission, resource-limited (CPU/mem/time), pulled from RabbitMQ by a worker pool. The Phase 6 Kubernetes profile replaces the host Docker socket with one short-lived Job per submission: the Dispatcher owns Kubernetes access, while a non-root Executor runs local Python/Java processes inside a resource-limited, deny-egress Pod. Both profiles preserve the same RabbitMQ event shapes and terminal result semantics; Docker images remain useful for fast local development, while Kubernetes is the safer deployment story.

## Real-time transport (implemented)

STOMP over WebSocket via a dedicated, standalone WS Gateway service — direct-connect from the frontend, not proxied through the API Gateway (that Gateway is a hand-rolled reactive HTTP proxy with no WebSocket-upgrade support, and retrofitting one wasn't worth it at this scale). JWT auth happens on the STOMP `CONNECT` frame's own headers (a browser can't set an `Authorization` header on a native WS upgrade request), validated by a `ChannelInterceptor` — structurally different from the API Gateway's per-HTTP-request `JwtAuthWebFilter` check.

Horizontal scaling: RabbitMQ's `match.events` topic exchange delivers each `duel.progress`/`match.completed` event to exactly one WS Gateway instance (competing-consumer queue). That instance immediately re-publishes the raw payload onto a Redis Pub/Sub channel every instance subscribes to; each instance's local Spring `SimpleBroker` only actually pushes to sessions it locally holds a subscription for (`/topic/duel/{matchId}`), so instances with no connected client for that match silently no-op. This replaces an explicit `matchId -> connectionId` lookup table with STOMP's own per-instance subscription registry — Redis Pub/Sub is the only piece that has to bridge instances. First Redis Pub/Sub usage in this repo; every other Redis use (matching sorted set, rate-limit token bucket) is request-response against stored state, not broadcast.

## Matchmaking algorithm (implemented)

Expanding-window ELO matching (like chess.com/League):
1. Client joins queue (`POST /matchmaking/queue/join`) → Matchmaking Service resolves the caller's current ELO from User/Profile Service (never trusts a client-supplied value), then publishes `JoinRequestedEvent{userId, elo, requestedAt}` to RabbitMQ's durable `matchmaking.join` queue (survives matchmaker restarts).
2. A `@RabbitListener` consumer inserts the user into a Redis sorted set keyed by ELO, plus a wait-start timestamp Hash - the only writer of that pool state, idempotent against RabbitMQ's at-least-once redelivery.
3. A periodic sweep (`@Scheduled`, ~1s) processes waiting users oldest-first; each user's acceptable ELO window widens with wait time (`base + growth × secondsWaited`, capped). A single atomic Redis Lua script (`pair_match.lua`) checks the candidate is still valid, scans for the nearest in-window opponent, and removes both in one step - the same atomicity technique the API Gateway's token-bucket rate limiter uses, applied to prevent two horizontally-scaled instances from double-booking the same opponent.
4. Match found → a random problem is assigned (Problem Service), a `Match` row is persisted in Matchmaking Service's own schema via the transactional-outbox pattern, and `match.created` is published to the `match.events` topic exchange for Duel Service, WS Gateway, and Leaderboard Service (Phase 4+) to consume independently.
5. A user waiting past a configured max (120s) is swept out and marked `EXPIRED`; `GET /matchmaking/queue/status` is polled by the client to learn `WAITING`/`MATCHED`/`EXPIRED`. `DELETE /matchmaking/queue/leave` cancels, with a race check against a concurrent match.

## Live duel flow (implemented)

1. Match found → the frontend navigates to `/duel/{matchId}`, which fetches initial state from Duel Service's `GET /duels/{matchId}` (problem ID, both player IDs, time limit, current progress, status) and opens a STOMP connection to WS Gateway, subscribing to `/topic/duel/{matchId}`. The REST fetch, not the WS push, is the source of truth for initial state — a client that opens the page slightly after `match.created` was broadcast, or reconnects mid-match, isn't left blind waiting for the next WS tick.
2. Players code independently (plain textarea, not Monaco — a named scope trade-off, not a silent one), submit via the normal Submission Service flow, submissions tagged `matchId`.
3. Judge Worker scores → result to RabbitMQ (`submission.judged`, now carrying `matchId` and `userId`) → Duel Service updates that player's progress % in its own match record (`max(existing, new)` — a worse resubmission never regresses the opponent-visible bar) → republishes `duel.progress` via its transactional outbox onto the `match.events` topic exchange.
4. WS Gateway relays `duel.progress` to `/topic/duel/{matchId}` — see "Real-time transport" above for the Redis Pub/Sub relay mechanics. Payload is **progress % only** (no code, no submission counts — preserves competitive integrity, keeps the payload simple).
5. **Win condition:** first to pass 100% test cases wins immediately, match closes to further scoring. If time limit expires with no 100% (`MatchTimeoutSweeper`, ~1s cadence, mirrors Matchmaking Service's own sweep pattern), higher progress % wins; exact tie = draw. The `IN_PROGRESS → COMPLETED` transition is guarded by JPA optimistic locking (`@Version`) so a near-simultaneous double-completion (both players hit 100% at once, or the timeout sweep fires as the last submission lands) can't double-apply — the losing writer gets `ObjectOptimisticLockingFailureException`, caught and treated as a no-op.
6. On completion: Duel Service computes ELO delta (standard ELO formula, K-factor 32) using each player's rating captured at match creation, updates its own match record, publishes `match.completed` (winner/loser/draw, both ELO deltas, both players' ELO-at-match-time) via the same outbox → User/Profile Service applies the delta and duel counters to its own row (sole writer of ELO — Duel Service never writes into Profile's table) → Leaderboard Service updates global, current ISO-week, and current calendar-quarter Redis sorted sets → WS Gateway pushes the final result to both clients over the same topic subscription.
   - Opponent's ELO-at-match-time (not their live post-match ELO) is what Profile Service sums into `sum_opp_elo_won/lost/drawn` — using live ELO would make the aggregate depend on when it's read, not what actually happened in that match.
   - Applying an ELO delta is not naturally idempotent (unlike, say, the outbox's `published_at IS NULL` check), so User Service guards against RabbitMQ's at-least-once redelivery with an explicit `profile.processed_match_completions(match_id)` dedup table, checked and written in the same transaction as the delta application.

## Deployment (implemented, Phase 6)

The platform deploys through one Helm chart at `deploy/helm/leetduel`. PostgreSQL, Redis, and RabbitMQ are pinned Bitnami dependencies with PVCs enabled by default. Application Services use Kubernetes DNS, startup/readiness/liveness probes, and `IfNotPresent` local images. NGINX Ingress exposes one Minikube hostname: `/` reaches Next.js, `/api/*` rewrites to the Gateway's existing paths, and `/ws` reaches the standalone STOMP gateway with long proxy timeouts. Gateway and Judge Dispatcher use standard CPU HPAs from 1 to 3 replicas at 70% utilization.

The supported local lifecycle is `scripts/bootstrap-minikube.ps1`, `scripts/build-minikube-images.ps1`, `scripts/deploy-minikube.ps1`, and the explicit destructive `scripts/reset-minikube-data.ps1`. Bootstrap selects Calico because the executor's deny-egress NetworkPolicy needs a network-policy-capable CNI. Secrets come from ignored `deploy/.env.k8s.local` and are materialized as the namespace-scoped `leetduel-secrets` Secret; no production-like credential is committed.

### Kubernetes-native judging

RabbitMQ remains the at-least-once async backbone. The Dispatcher acknowledges `judge.job.created` only after it has created or found `judge-<submissionId>` and its immutable `judge-input-<submissionId>` ConfigMap. Deterministic naming makes redelivery a reuse operation rather than a second execution. On startup and on a schedule, the Dispatcher finds completed Jobs, reads the last `LEETDUEL_RESULT:<json>` frame from Pod logs, publishes `submission.judged`, and deletes the Job and ConfigMap only after publication. A crash before publication leaves the completed Job for reconciliation; a crash after publication can duplicate the event, and Submission Service's terminal-status guard makes that harmless.

The Executor has no RabbitMQ credentials, Kubernetes token, or outbound network access. It reads the existing `JudgeJobCreatedEvent` from a read-only ConfigMap, runs exactly one submission with local Python/Java processes, and writes exactly one framed result. The Pod uses non-root execution, dropped capabilities, `allowPrivilegeEscalation=false`, read-only root filesystem, `RuntimeDefault` seccomp, writable `/tmp` and `/sandbox` `emptyDir` mounts, CPU/memory limits, `backoffLimit: 0`, and an active deadline. This accepts Kubernetes Job cold-start latency and local Minikube limits in exchange for removing Judge Worker's host Docker socket; Kafka replay, KEDA, TLS, CI/CD, and production multi-tenant hardening remain deferred.

## Auth / Gateway (implemented)

Spring Cloud Gateway in front of everything: JWT validation, routing, rate limiting. Separate Auth Service issues JWTs (email/password + Spring Security, refresh-token rotation). Keeps whole stack in Spring ecosystem.

Rate limiting is a token bucket, not a fixed window: bucket state (tokens + last-refill timestamp) lives in one Redis Hash key per client, refilled/consumed by a single atomic Lua `EVAL` (`scripts/token_bucket.lua`). Single-key-per-client makes this safe on a real Redis Cluster with no code changes (no CROSSSLOT risk from multi-key scripts), and the atomic EVAL removes the check-then-act race a plain GET/SET or INCR/EXPIRE pair would have under concurrent requests.

## Observability (v1 scope, planned Phase 7)

Prometheus + Grafana only for v1 (Spring Boot Actuator + Micrometer exposing `/actuator/prometheus`, scraped via kube-prometheus-stack). Distributed tracing (OpenTelemetry/Jaeger), centralized logging (ELK/Loki), and Resilience4j circuit breakers/rate limiting are explicitly deferred to a later phase — not v1 scope, called out here so it isn't forgotten, but v1 should not creep into building them.

## Frontend

Full React + TypeScript SPA: Monaco code editor, problem browser, matchmaking/queue screen, live duel view (WS-driven opponent progress bar), leaderboard, profile/stats/rating history, responsive navigation, accessible state handling, and deterministic Vitest/Testing Library coverage. Phase 5 verification includes 10 frontend tests, lint, typecheck, an offline production build, and desktop/mobile `agent-browser` acceptance checks. The product uses professional match/challenge copy while retaining duel terminology only for technical routes and service contracts.

## Open questions / deferred decisions

- Historical leaderboard rebuild/replay strategy if Redis state is lost (the current leaderboard is a derived materialized view)
- Judge sandbox anti-cheat considerations (plagiarism detection is explicitly out of v1 scope unless revisited)
- Broader CI/CD coverage and contract-test automation beyond the Phase 5 frontend/API checks
- Production Kubernetes hardening and remote-registry policy
These get resolved incrementally as each phase is implemented, not up front. (Matchmaking join-request expiry is resolved as of Phase 2; Duel/Match's schema, WS auth, and cross-instance fanout are resolved as of Phase 3 — see "Live duel flow" and "Real-time transport" above.)

## Implementation phases

0. **Foundations — done.** Repo structure, `docker-compose.infra.yml` (Postgres, Redis, RabbitMQ), Auth Service, API Gateway, User Service — login working end-to-end.
1. **Core judge loop — done.** Problem Service, Submission Service, Judge Worker (Docker sandbox), RabbitMQ job queue — single-player practice mode fully working.
2. **ELO + Matchmaking — done.** Matchmaking Service, Redis expanding-window pairing via an atomic Lua script, transactional-outbox `match.created` publish.
3. **Real-time duel — done.** Duel Service (match lifecycle, optimistic-lock-guarded win/timeout logic, ELO calculator, transactional-outbox `duel.progress`/`match.completed` publish), WS Gateway (STOMP over WebSocket, JWT-on-CONNECT auth, RabbitMQ → Redis Pub/Sub → local `SimpleBroker` fanout), `matchId`/`userId` threaded through Submission Service and Judge Worker, User Service's `match.completed` consumer (sole ELO writer, idempotency-guarded), live duel frontend page.
4. **Leaderboard + profile/stats — done.** Leaderboard Service consumes `match.completed` into Redis global/weekly/season sorted-set projections with Lua-backed idempotency for additive period scores; User Service stores transactional ELO history; Duel Service exposes paginated match history; frontend exposes public `/leaderboard` and authenticated `/profile` views.
5. **Ship-ready frontend — done.** Monaco editor with per-language draft preservation; responsive shared navigation; accessible loading, empty, error, retry, and 404 states; public username and problem-summary batch reads; frontend component tests; and desktop/mobile browser acceptance checks.
6. **Kubernetes deployment — done.** One Helm chart, pinned stateful dependencies with PVCs, same-origin Ingress, externalized service DNS, probes, HPAs, Minikube scripts, and Kubernetes-native Judge Dispatcher/Executor Jobs. Docker Compose remains the local development profile.
7. Observability: Prometheus + Grafana dashboards (service health, queue depth, judge latency, match wait time, per-service request rate/latency/error rate).
