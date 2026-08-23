# LeetDuel

LeetCode-style problem practice platform + real-time 1v1 ranked duels (ELO-matched), built as a microservices system deliberately covering every major [system design primer](https://github.com/donnemartin/system-design-primer) component: async messaging, caching, service-to-service communication, real-time client-server-client push, sandboxed code execution, container orchestration, and observability.

**Status: design/planning phase.** No application code yet — see `goals.md` for the full architecture and `LEARN_AND_BUILD.md` for the learning + build roadmap. Local infra (Postgres, MongoDB, Redis, RabbitMQ + their admin UIs) is defined and runnable.

## Why this project exists

Built as a resume/interview-prep project targeting backend/systems engineering roles — every design decision is made to be defensible in a systems design interview, not just to ship a feature. See `CLAUDE.md` for the standing "explain the why" convention used throughout this repo's history.

## Architecture at a glance

| Service | Responsibility | Store |
|---|---|---|
| API Gateway | Spring Cloud Gateway, JWT validation, routing, rate limiting | — |
| Auth Service | signup/login, JWT issuance | Postgres |
| User/Profile Service | profile, current ELO, match history | Postgres |
| Problem Service | problem CRUD, test cases, tags/difficulty | Postgres |
| Submission Service | accepts code, publishes judge job | Postgres |
| Judge Worker (pool) | Docker-sandboxed code execution against test cases | MongoDB |
| Matchmaking Service | RabbitMQ join queue + Redis expanding-window ELO pairing | Redis |
| Duel/Match Service | live match lifecycle, win condition, ELO update | Postgres |
| WS Gateway Service | WebSocket connections, Redis pub/sub fanout | Redis |
| Leaderboard Service | ranked leaderboard from match results | Redis |

**Async backbone:** RabbitMQ (task queues + topic-exchange event fanout). **Data:** polyglot — Postgres for relational/transactional core, MongoDB for high-write variable-shape submission results, Redis for hot/ephemeral state (matching queue, leaderboard, WS sessions, cache). Full reasoning for every choice, including what was deliberately left out (Kafka, distributed tracing, circuit breakers) and why, is in `goals.md`.

**Deployment target:** Kubernetes, with Prometheus + Grafana for observability.

## Repo contents (current)

- `goals.md` — full design discussion: architecture, matchmaking algorithm, duel flow, data strategy, deployment, and what's still open.
- `LEARN_AND_BUILD.md` — learning resources per topic, followed by a phase-by-phase build plan from a blank folder to a deployed product.
- `CLAUDE.md` — standing instruction for AI-assisted work in this repo: always explain the "why," not just the "what."
- `docker-compose.infra.yml` — local dev infra: Postgres, MongoDB, Redis, RabbitMQ, plus admin/observability UIs (pgAdmin, Mongo Express, RedisInsight, RabbitMQ management console).

## Running local infra

```bash
docker compose -f docker-compose.infra.yml up -d
```

| Service | URL | Credentials |
|---|---|---|
| RabbitMQ management | http://localhost:15672 | `leetduel` / `leetduel_dev` |
| pgAdmin | http://localhost:5050 | `dev@leetduel.com` / `leetduel_dev` |
| Mongo Express | http://localhost:8081 | `leetduel` / `leetduel_dev` |
| RedisInsight | http://localhost:5540 | — |

All credentials above are throwaway local-dev defaults, not used anywhere outside this compose network.

## Roadmap

Phased build order (details in `LEARN_AND_BUILD.md`): foundations (auth, gateway, user service) → core judge loop (practice mode) → ELO + matchmaking → real-time duel (WebSocket) → leaderboard/profile → React frontend → Kubernetes deployment → observability.
