# LeetDuel

A LeetCode-style problem practice platform with real-time, ELO-ranked 1v1 coding duels — built as a microservices system to exercise the full breadth of a modern backend/systems design: async messaging, polyglot persistence, sandboxed untrusted-code execution, real-time push, and Kubernetes deployment with metrics.

## Executive summary

LeetDuel is a from-scratch distributed system, not a CRUD app with a queue bolted on. Each service owns one responsibility and one data store; services talk to each other over RabbitMQ (task queues + topic-exchange fanout) and Redis (hot state, rate limiting), never by reaching into another service's database. The judge itself runs untrusted user code inside locked-down, ephemeral Docker containers — no network, non-root, read-only root filesystem, resource-limited — the same class of problem Judge0 and competitive-programming judges solve in production.

## What it does

- **Auth:** email/password signup with verification, login, refresh-token rotation, password reset, Google OAuth — all JWT-based, validated at the API Gateway before any request reaches a backend service.
- **Practice mode:** browse problems by tag/difficulty, write a solution in Python or Java, submit it, get back a per-test-case verdict from a sandboxed judge worker.
- **Ranked duels:** join a queue, get ELO-matched against an opponent within an expanding rating window, race to solve the same problem live, opponent's progress bar updates over WebSocket, ELO adjusts on completion.

## System design

```mermaid
flowchart TB
    FE["Frontend<br/>Next.js / React SPA"]

    FE -->|"HTTPS + JWT"| GW["API Gateway<br/>hand-rolled reactive proxy (WebFlux)"]
    FE -.->|"STOMP over WebSocket<br/>JWT on CONNECT frame"| WSG

    GW -->|"JWT verify"| AUTH["Auth Service"]
    GW --> USER["User / Profile Service"]
    GW --> PROB["Problem Service"]
    GW --> SUB["Submission Service"]
    GW --> MATCH["Matchmaking Service"]
    GW --> DUEL["Duel Service"]
    GW -.->|"token-bucket check<br/>(Lua EVAL, atomic)"| REDIS[("Redis")]

    AUTH -->|"transactional outbox"| MQ[("RabbitMQ")]
    SUB -->|"publish judge job"| MQ
    MQ -->|"consume"| JUDGE["Judge Worker Pool"]

    JUDGE -->|"spawn per-submission<br/>sibling container"| SANDBOX[["Docker sandbox<br/>Python 3.12 / Java 21<br/>no network, non-root, RO rootfs"]]
    JUDGE --> MONGO[("MongoDB<br/>per-test-case results")]
    JUDGE -->|"submission.judged<br/>(matchId, userId)"| MQ

    MATCH -->|"expanding-window ELO pairing<br/>atomic Lua script"| REDIS
    MATCH -->|"match.created<br/>(transactional outbox)"| MQ
    MQ -->|"consume"| DUEL
    DUEL -->|"duel.progress / match.completed<br/>(transactional outbox)"| MQ
    MQ -->|"consume"| WSG["WS Gateway<br/>(standalone, direct-connect)"]
    MQ -->|"consume match.completed<br/>(sole ELO writer)"| USER
    WSG -.->|"Pub/Sub relay<br/>(cross-instance fanout)"| REDIS

    AUTH --> PG[("PostgreSQL")]
    USER --> PG
    PROB --> PG
    SUB --> PG
    MATCH --> PG
    DUEL --> PG

    subgraph planned [" Phase 4+ "]
        LEAD["Leaderboard Service"]
    end
    MQ -.-> LEAD
```

Full design rationale — service boundaries, delivery guarantees, ELO/matchmaking algorithm, data-store choices — is written up in [`docs/goals.md`](docs/goals.md).

## Tech stack

| Layer | Choice |
|---|---|
| Backend services | Java 21, Spring Boot 4.1 (WebMVC + WebFlux for the reactive Gateway), Spring Security, Spring Data JPA, Flyway |
| API Gateway | Hand-rolled reactive HTTP proxy (Spring WebFlux, not Spring Cloud Gateway) — JWT validation, routing, Redis-backed token-bucket rate limiting. No WebSocket-upgrade support (see WS Gateway below). |
| Auth | JWT (JJWT/HS256), refresh-token rotation, Google OAuth2, transactional outbox for event publishing |
| Real-time duel | STOMP over WebSocket via a standalone WS Gateway service — JWT verified on the STOMP CONNECT frame, RabbitMQ → Redis Pub/Sub → local Spring `SimpleBroker` relay for horizontal scaling |
| Async messaging | RabbitMQ — task queues (judge jobs, matchmaking join requests) + topic exchange (event fanout: `match.created`/`duel.progress`/`match.completed`) |
| Data stores | PostgreSQL (relational core: users, problems, submissions/match metadata), MongoDB (variable-shape per-test-case judge output), Redis (rate limiting, ELO matching index, WS Pub/Sub fanout; leaderboard once built) |
| Code execution | Docker (`docker-java` client) — ephemeral, resource-limited sandbox containers per submission |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS 4, `@stomp/stompjs` |
|Planned  | Kubernetes + Helm, Prometheus + Grafana (Actuator/Micrometer already wired per-service), Leaderboard Service |

## How to run

**Prerequisites:** Docker Desktop, JDK 21, Node.js 20+.

1. **Start local infra** (Postgres, MongoDB, Redis, RabbitMQ, admin UIs, and the three containerized app services — Judge Worker, Duel Service, WS Gateway):
   ```bash
   docker compose -f docker-compose.infra.yml up -d
   ```

2. **Build the judge's sandbox images** (one-time, or after changing `docker/sandbox-*`):
   ```powershell
   ./scripts/build-sandbox-images.ps1
   ```

3. **Run each remaining Spring Boot service** (each is an independent Gradle project; Judge Worker, Duel Service, and WS Gateway already run via docker-compose above):
   ```bash
   cd services/auth-service && ./gradlew bootRun          # :8082
   cd services/user-service && ./gradlew bootRun          # :8083
   cd services/gateway && ./gradlew bootRun               # :8084
   cd services/problem-service && ./gradlew bootRun       # :8085
   cd services/submission-service && ./gradlew bootRun    # :8086
   cd services/matchmaking-service && ./gradlew bootRun   # :8088
   ```
   `auth-service` needs `GMAIL_USERNAME`/`GMAIL_APP_PASSWORD` env vars for verification emails (a Gmail app password, not the account password) and optionally `JWT_SECRET` (falls back to a dev-only default otherwise, shared by `gateway` and `ws-gateway` too).

4. **Run the frontend:**
   ```bash
   cd frontend && npm install && npm run dev
   ```
   Open `http://localhost:3000`. `frontend/.env.local` points `NEXT_PUBLIC_AUTH_API_URL` at `auth-service` (`:8082`), `NEXT_PUBLIC_GATEWAY_API_URL` at the Gateway (`:8084`), and `NEXT_PUBLIC_WS_GATEWAY_URL` at WS Gateway (`:8090`).

**Admin UIs** (brought up by step 1, throwaway local-dev credentials `leetduel` / `leetduel_dev`):

| UI | URL |
|---|---|
| RabbitMQ management | http://localhost:15672 |
| pgAdmin | http://localhost:5050 |
| Mongo Express | http://localhost:8081 |
| RedisInsight | http://localhost:5540 |
