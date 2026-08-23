# LeetDuel — Project Goals & Design Discussion

Status: design in progress (brainstorming phase, not yet finalized into full spec/plan).
Target: backend-heavy systems project demonstrating full system-design-primer coverage for top systems/backend engineering roles.

## Pitch

LeetCode-style problem practice platform + real-time 1v1 ranked duels (ELO-matched), built on microservices to deliberately exercise every major system design primer component: async messaging, caching, service-to-service communication, real-time client-server-client push, container orchestration, and observability.

## Scope decision

Full platform, built in phases. Each phase independently demoable and resume-worthy (not a big-bang build).

## Services

| Service | Responsibility | Store |
|---|---|---|
| API Gateway | Spring Cloud Gateway, JWT validation, routing, rate limiting | — |
| Auth Service | signup/login, JWT issuance (OAuth2/Spring Security) | Postgres |
| User/Profile Service | profile, current ELO, match history view | Postgres |
| Problem Service | problem CRUD, test cases, tags/difficulty | Postgres |
| Submission Service | accepts code, publishes judge job | Postgres (metadata) |
| Judge Worker (pool) | consumes RabbitMQ job, spins Docker sandbox, runs code vs test cases, scores, emits result | MongoDB (per-test-case output/logs, code snapshot) |
| Matchmaking Service | RabbitMQ join queue in, Redis sorted-set expanding-window ELO pairing | Redis (matching index) |
| Duel/Match Service | owns live match lifecycle, both players' progress, decides winner, triggers ELO update | Postgres (match record) |
| WS Gateway Service | holds client WebSocket connections, fanout via Redis pub/sub, routes opponent progress via Redis `matchId -> connectionIds` | Redis (pub/sub, session map) |
| Leaderboard Service | consumes match-completed events off RabbitMQ, maintains ranked leaderboard | Redis (sorted set) |

## Async backbone (revised — Kafka dropped as overkill for this project's actual scale)

- **RabbitMQ** — does everything async: judge job queue (task-queue semantics, worker pool consumes), matchmaking join-request queue, and match event fanout via a topic exchange (multiple bound queues: Duel Service, WS Gateway, Leaderboard Service each get `match.created` / `match.completed` / `duel.progress`).
- **Redis** — matching index (sorted set by ELO), leaderboard (sorted set), WS session/connection registry + pub/sub for cross-instance fanout, general cache (hot problem statements, user profiles).
- Explicit trade-off accepted: no Kafka means no durable event replay / long retention / event-sourcing story. Fine at this scale — the two remaining tools (RabbitMQ, Redis) each still have a genuine, explainable reason for being there, which was the actual goal (demonstrating *when/why* to reach for a tool, not raw scale).

## Data strategy

Polyglot, database-per-service where it matters:
- **Postgres** — core relational/transactional entities: users, problems + test cases, matches, ELO history. ACID needed.
- **MongoDB** — high-write, variable-shape submission execution results (code snapshot, per-test-case output/logs).
- **Redis** — ephemeral/hot state only (matching queue, leaderboard, WS sessions, cache). Not source of truth for anything durable.

## Code execution / judging

Docker sandbox workers: isolated containers per submission, resource-limited (CPU/mem/time), pulled from RabbitMQ job queue by a worker pool. Real isolation/security story (container escape prevention, resource limits, timeout kill) — not a subprocess/ulimit shortcut.

## Real-time transport

WebSocket via a dedicated WS Gateway service. Connections registered in Redis so gateway can run multi-instance (critical: without the Redis `matchId -> connectionIds` lookup, horizontal scaling breaks opponent-push routing — this is the actual reason Redis pub/sub is used instead of plain in-memory WS state).

## Matchmaking algorithm

Expanding-window ELO matching (like chess.com/League):
1. Client joins queue → publishes `join_request{userId, elo}` to RabbitMQ `matchmaking.join` queue (durable, at-least-once, survives matchmaker restarts).
2. Matchmaker Service consumes, inserts into Redis sorted set keyed by ELO.
3. On each insert, scans sorted set for a pair within current acceptable rating gap; gap widens the longer either user has waited (bounds wait time while preserving fairness early on).
4. Match found → both removed from Redis set, Match record created, `match.created` published to RabbitMQ topic exchange (fans out to Duel Service + WS Gateway).

## Live duel flow

1. Match found → both clients pushed `match.created` over WS (problem ID, opponent username/ELO, time limit e.g. 20 min).
2. Players code independently (React/Monaco editor), submit via normal Submission Service flow, submissions tagged `matchId`.
3. Judge Worker scores → result to RabbitMQ → Duel Service updates that player's progress % in match record → republishes `duel.progress` on topic exchange.
4. WS Gateway consumes `duel.progress`, looks up opponent's connection via Redis, pushes **progress bar only** (no code, no submission counts — preserves competitive integrity, keeps payload simple).
5. **Win condition:** first to pass 100% test cases wins immediately, match closes to further scoring. If time limit expires with no 100%, higher progress % wins; exact tie = draw.
6. On completion: Duel Service computes ELO delta (standard ELO formula, K-factor ~32), updates Postgres, publishes `match.completed` → Leaderboard Service updates Redis sorted set → WS Gateway pushes final result to both clients.

## Deployment

Kubernetes. Real manifests/Helm charts, k8s-native service discovery (no separate Eureka layer — redundant once on k8s), HPA autoscaling, rolling deploys. Local dev via kind/minikube or Docker Desktop's k8s, portable to any cloud.

## Auth / Gateway

Spring Cloud Gateway in front of everything: JWT validation, routing, rate limiting. Separate Auth Service issues JWTs (OAuth2/Spring Security). Keeps whole stack in Spring ecosystem.

## Observability (v1 scope)

Prometheus + Grafana only for v1 (Spring Boot Actuator + Micrometer exposing `/actuator/prometheus`, scraped via kube-prometheus-stack). Distributed tracing (OpenTelemetry/Jaeger), centralized logging (ELK/Loki), and Resilience4j circuit breakers/rate limiting are explicitly deferred to a later phase — not v1 scope, called out here so it isn't forgotten, but v1 should not creep into building them.

## Frontend

Full React + TypeScript SPA: Monaco code editor, problem browser, matchmaking/queue screen, live duel view (WS-driven opponent progress bar), leaderboard, profile/stats/rating history.

## Open / not yet decided (still needs a brainstorming pass before full spec is final)

- Exact data model / schema per service (tables, fields, indexes)
- Judge sandbox security specifics: which language runtimes, container base images, exact resource limits (CPU/mem/timeout), how test cases are fed in/results captured, anti-cheat considerations (plagiarism detection is explicitly out of v1 scope unless revisited)
- Phased build order with concrete milestones per phase (draft order below, not yet confirmed)
- Testing strategy per service (unit/integration/e2e, contract tests between services)
- CI/CD pipeline
- Exact k8s manifest/Helm chart layout, local dev docker-compose file for pre-k8s phases
- Stale/expired matchmaking join request handling (max-wait/expiry) — raised, not yet resolved

## Draft phased build order (not yet confirmed)

0. Foundations: repo structure, docker-compose for local infra (Postgres, Mongo, Redis, RabbitMQ), Auth Service, API Gateway, User Service — login working end-to-end.
1. Core judge loop: Problem Service, Submission Service, Judge Workers (Docker sandbox), RabbitMQ job queue — single-player practice mode fully working.
2. ELO + Matchmaking: rating model, Matchmaking Service, Redis expanding-window pairing.
3. Real-time duel: WS Gateway, Redis pub/sub fanout, live duel state, ELO update on completion.
4. Leaderboard + profile/stats.
5. Frontend React SPA (likely built incrementally alongside each phase's API surface rather than as one final phase).
6. Kubernetes deployment: Helm charts, HPA, migrate from docker-compose to k8s.
7. Observability: Prometheus + Grafana dashboards (service health, queue depth, judge latency, match wait time, per-service request rate/latency/error rate).

## Next steps

Finish remaining open items above via more brainstorming, then write full design spec to `docs/superpowers/specs/`, then hand off to writing-plans skill for implementation plan.
