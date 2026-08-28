# LeetDuel architecture

## Systems pitch

LeetDuel separates synchronous user-facing reads from asynchronous work that can be retried. The API Gateway handles HTTP composition and policy, service-owned PostgreSQL schemas hold durable state, Redis handles coordination and materialized views, RabbitMQ carries at-least-once commands/events, and the Judge Dispatcher creates one isolated Kubernetes Job per submission.

The design optimizes for explainability and failure recovery rather than minimum service count. A service can restart without owning another service's tables, and a consumer can safely process a redelivered message through an idempotency key or an absolute projection update.

## Ownership map

| Boundary | Owns | Does not own |
|---|---|---|
| Auth Service | Credentials, refresh-token rotation, canonical username, auth outbox | Profile statistics or leaderboard ranking |
| User Service | Profile projection, ELO, W/L/D stats, rating history, match-completion deduplication | Passwords or Redis leaderboard state |
| Problem Service | Problem metadata, test cases, language stubs, internal catalog projection | User attempts or recommendation scores |
| Submission Service | Submission lifecycle, public verdict/result, judge-job outbox | Code execution or practice explanations |
| Practice Intelligence | Attempts, sticky solved state, weak-topic signals, embeddings, recommendations, coaching jobs | Hidden tests, reference solutions, canonical problem records |
| Matchmaking Service | Waiting queue and match creation | Match lifecycle or ELO mutation |
| Duel Service | Match lifecycle, progress, winner/timeout resolution, match outbox | User profile writes or ranking projections |
| Leaderboard Service | Redis global/weekly/season ranking projections | Durable user identity |
| WS Gateway | Authenticated STOMP sessions and cross-instance fanout | Durable match state |
| Judge Dispatcher | RabbitMQ consumption, deterministic Job lifecycle, result publication | Untrusted code credentials or business persistence |
| Judge Executor | One submission execution and one framed result | RabbitMQ, Kubernetes API, database, or network access |

## Request and event paths

### Practice submission

1. The frontend sends code to Submission Service through the Gateway.
2. Submission Service writes submission metadata and an outbox row in one PostgreSQL transaction.
3. The outbox relay publishes `judge.job.created` to a direct RabbitMQ work exchange.
4. Judge Dispatcher acknowledges the message only after it creates or finds `judge-<submissionId>` and its immutable input ConfigMap.
5. The Executor reads the ConfigMap, runs exactly one local Python or Java process, and emits one `LEETDUEL_RESULT:<json>` log frame.
6. The Dispatcher parses the frame, publishes `submission.judged`, then deletes the Job and ConfigMap.
7. Submission Service applies terminal status idempotently. Practice-only completion data is emitted separately so source code does not enter shared duel consumers.
8. Practice Intelligence records the attempt, updates progress/recommendations, and schedules validated AI coaching outside the database transaction.

The durable write precedes publication, so a producer crash leaves the outbox row for retry. A Dispatcher crash before publication leaves a completed Job for reconciliation. A crash after publication can duplicate the result event, which is harmless because terminal submission updates are idempotent.

### Ranked match

1. Matchmaking stores a waiting user in a Redis sorted-set/index and pairs candidates with an atomic Lua operation.
2. Matchmaking persists the match request/result and publishes `match.created` through its outbox.
3. Duel Service creates the match lifecycle, accepts judged submissions, and publishes progress/final completion through its own outbox.
4. User Service is the sole ELO writer and appends rating history in the same transaction as the match-completion dedup record.
5. Leaderboard Service consumes completion events into Redis sorted sets. The frontend resolves public usernames through one bounded batch request.
6. WS Gateway relays progress through RabbitMQ, Redis Pub/Sub, and each instance's local STOMP broker. REST remains the reconnect source of truth.

## Consistency model

- PostgreSQL is the durable source of truth for business records and service-owned schemas.
- Redis is a derived projection or coordination store. Cache loss causes recomputation or temporary unavailability, not data loss.
- RabbitMQ is at least once. Consumers use submission IDs, match IDs, or atomic projection scripts as idempotency keys.
- Public usernames and problem summaries are eventually consistent projections. Missing data falls back to a short ID or the existing problem identifier.
- WebSocket messages are transient notifications. The client refetches authoritative REST state after reconnect or missed messages.

## Scaling boundaries

The Gateway and Judge Dispatcher have CPU HPAs from one to three replicas in the Minikube profile. Judge throughput scales independently through short-lived Jobs, avoiding a fixed worker pool, while the Dispatcher remains the control-plane bottleneck to monitor. PostgreSQL-backed services use explicit HikariCP bounds so replica growth cannot silently create an unbounded connection storm.

The current local capacity model is documented in [`PERFORMANCE.md`](PERFORMANCE.md). It is a planning model, not a benchmark: real limits depend on query plans, payload size, JVM heap, Minikube CPU contention, provider latency, and the configured PostgreSQL connection limit.

## Deliberate trade-offs

- RabbitMQ is simpler than Kafka for bounded work queues and topic fanout, but the project does not get a long-lived replay log.
- PostgreSQL JSONB and pgvector avoid another durable database, but very large-scale vector search would need a dedicated retrieval system.
- Kubernetes Jobs remove host Docker socket access, but cold-start latency is accepted for local practice submissions.
- Frontend composition avoids cross-service joins, but identity and problem enrichment can briefly show fallback values during event propagation.
- No tracing, centralized logging, KEDA, TLS, remote registry, or anti-cheat system is added in this phase. These are production-hardening concerns, not prerequisites for the core correctness story.
