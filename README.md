# LeetDuel

LeetCode-style problem practice platform with real-time 1v1 ELO-matched duels. Microservices, async messaging, polyglot persistence, WebSocket real-time, Kubernetes deployment.

Status: design/planning phase. No application code yet. Full design in `goals.md`, build plan in `LEARN_AND_BUILD.md`. Local infra runnable now.

## Architecture

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

Async: RabbitMQ (task queues + topic-exchange fanout). Data: Postgres (relational core), MongoDB (submission results), Redis (matching queue, leaderboard, WS sessions, cache). Deploy target: Kubernetes, Prometheus + Grafana for metrics.

## Repo contents

- `goals.md` — architecture, matchmaking algorithm, duel flow, data strategy, open questions.
- `LEARN_AND_BUILD.md` — learning resources + phase-by-phase build plan.
- `docker-compose.infra.yml` — local infra: Postgres, MongoDB, Redis, RabbitMQ + admin UIs.

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

Foundations → core judge loop → ELO + matchmaking → real-time duel → leaderboard/profile → frontend → Kubernetes → observability. Details in `LEARN_AND_BUILD.md`.
