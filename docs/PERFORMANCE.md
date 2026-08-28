# Performance and capacity model

## What is measured versus estimated

The repository currently has deterministic configuration and a reproducible capacity calculation, not a production load-test result. The numbers below are planning bounds derived from explicit assumptions. They should be replaced with benchmark observations after Phase 8 adds metrics and a repeatable load harness.

Run the baseline calculation from the repository root:

```powershell
./scripts/calculate-capacity.ps1
```

Override assumptions for a different deployment:

```powershell
./scripts/calculate-capacity.ps1 -DbServiceReplicas 2 -DbPoolMax 12 -JudgeSlots 8 -AverageJudgeSeconds 6
```

## Baseline assumptions

| Area | Assumption | Calculation |
|---|---:|---|
| PostgreSQL services | 7 services, 1 replica each | Auth, User, Problem, Submission, Matchmaking, Duel, Practice Intelligence |
| HikariCP | 8 max connections, 1 minimum idle per instance | 7 × 1 × 8 = 56 possible pooled connections |
| Database reserve | 12 connections | Leaves 32 connections under a 100-connection planning budget |
| Average DB hold time | 25 ms | 56 / 0.025 = 2,240 theoretical transactions/sec upper bound |
| Judge execution | 4 concurrent Jobs, 8 seconds average | 4 / 8 = 0.5 submissions/sec, or 30/minute |
| Gateway | 3 replicas, 75 requests/sec each | 225 requests/sec planning target |
| WebSocket gateway | 3 replicas, 1,000 sessions each | 3,000 concurrent sessions planning target |

The database row is a Little's Law upper bound for active connections, not a benchmark or a promise that PostgreSQL can execute 2,240 useful transactions per second. Query cost, locks, WAL, disk latency, and CPU become the real limit first. Likewise, the Gateway and WebSocket values are explicit test targets, not observed production throughput.

## Pooling policy

Spring Boot already supplies HikariCP through the JDBC/JPA starters. Each PostgreSQL-backed service now makes the important limits explicit:

- `DB_POOL_MAX_SIZE` defaults to 8 per instance.
- `DB_POOL_MIN_IDLE` defaults to 1, avoiding seven permanently warm pools in a quiet local demo.
- `DB_POOL_CONNECTION_TIMEOUT_MS` defaults to 2,000 ms, so overload fails quickly instead of accumulating unbounded request threads.
- `DB_POOL_IDLE_TIMEOUT_MS` and `DB_POOL_MAX_LIFETIME_MS` allow idle connections to be recycled before infrastructure-side timeouts.
- `DB_POOL_LEAK_DETECTION_MS` is disabled by default and can be enabled during diagnosis without paying the normal production logging cost.

The budget is `sum(service replicas × pool maximum) + admin/migration reserve`. Increasing a pool does not increase database capacity; it only permits more concurrent database work and can make overload worse. If a service is scaled to two replicas, either lower its per-instance pool or verify the database connection budget first.

## Judge backlog model

For an average arrival rate `λ` and aggregate judge service rate `μ = slots / average execution seconds`, utilization is `ρ = λ / μ`. The baseline can sustain a steady 0.25 submissions/sec at 50% modeled utilization. A burst of 100 submissions has 0.25 submissions/sec of spare drain capacity, so the simplified drain estimate is 400 seconds after the burst arrives.

This intentionally models the executor as the throughput boundary. RabbitMQ absorbs transient bursts, while deterministic Jobs provide isolation. The model does not claim zero queue latency because Kubernetes scheduling and image/cache state dominate local cold-start time.

## Scaling decisions

- Scale Gateway replicas when CPU or request latency rises; its Redis rate-limit Lua script remains single-key atomic per client.
- Scale Judge Dispatcher replicas for control-plane and reconciliation throughput; deterministic Job names make redelivered messages safe across replicas.
- Scale judge execution through independent Jobs, subject to namespace CPU/memory quotas and the PostgreSQL/RabbitMQ write path.
- Do not scale database pools blindly with application replicas. Connection budget and transaction hold time must be rechecked together.
- Add metrics and load tests in Phase 8 before presenting these values as measured SLOs.
