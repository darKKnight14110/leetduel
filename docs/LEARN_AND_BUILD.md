# LeetDuel — Build Plan & Reference Material

Companion to `goals.md`. This file pairs the phase-by-phase build plan with the reference material behind each phase's technical decisions.

---

## Part 1 — Reference material by topic

Grouped by the technical cluster each phase draws on.

### 1. Spring Boot & microservices fundamentals
- [Spring Boot 4 — Microservices portfolio project with Java, Full Course (2026)](https://www.youtube.com/watch?v=vE3u4zgMai8)
- [Java Spring Boot Microservices eCommerce Project — Docker, PostgreSQL, DevOps](https://www.youtube.com/watch?v=i_9z3tNrphE)
- [Spring Boot Microservices Complete Tutorial (playlist)](https://www.youtube.com/playlist?list=PLuNxlOYbv61g_ytin-wgkecfWDKVCEDmB)

### 2. API Gateway
- [Spring.io official guide — Building a Gateway](https://spring.io/guides/gs/gateway/)
- [Baeldung — Exploring the New Spring Cloud Gateway](https://www.baeldung.com/spring-cloud-gateway)
- [Spring Cloud Gateway project page](https://spring.io/projects/spring-cloud-gateway/)

### 3. Auth: Spring Security + JWT + OAuth2
- [Baeldung — Using JWT with Spring Security OAuth](https://www.baeldung.com/spring-security-oauth-jwt)
- [Java Chinna — Secure Spring REST API with OAuth2 JWT Authentication (Spring Security 6, key pairs, resource server)](https://www.javachinna.com/secure-spring-rest-api-oauth2-jwt-authentication/)

### 4. RabbitMQ (queues, exchanges, routing)
- [RabbitMQ official tutorial — Routing (Spring AMQP)](https://www.rabbitmq.com/tutorials/tutorial-four-spring-amqp)
- [RabbitMQ official tutorial — Publish/Subscribe (Spring AMQP)](https://rabbitmq.com/tutorials/tutorial-three-spring-amqp.html)
- [Baeldung — RabbitMQ Message Dispatching with Spring AMQP](https://www.baeldung.com/rabbitmq-spring-amqp)

Core distinction this project rests on: a **queue** (point-to-point, one consumer takes the message — the judge job queue and matchmaking join queue) versus a **topic exchange** (fanout to multiple bound queues — the `match.created`/`duel.progress` broadcast to Duel Service + WS Gateway + Leaderboard Service).

### 5. Redis (sorted sets, pub/sub)
- [GeeksforGeeks — Complete Guide to Redis Publish Subscribe](https://www.geeksforgeeks.org/system-design/redis-publish-subscribe/)
- [Redis Crash Course (Udemy) — sorted sets for ranking/leaderboards, pub/sub for real-time messaging](https://www.udemy.com/course/learn-redis-fast-the-complete-crash-course/)

Relevant primitives: `ZADD`/`ZRANGEBYSCORE`/`ZRANGEBYSCORE WITHSCORES` for the ELO matching window and leaderboard; `PUBLISH`/`SUBSCRIBE` for cross-instance WS fanout; `EVAL`/Lua scripting for the atomic token-bucket rate limiter (implemented in the Gateway).

### 6. WebSocket + STOMP real-time
- [Spring Boot WebSocket Tutorial — Real-Time Chat App with STOMP & Java (YouTube)](https://www.youtube.com/watch?v=LF3Tn41j6Oc)
- [Toptal — Using Spring Boot for WebSocket Implementation with STOMP](https://www.toptal.com/developers/java/stomp-spring-boot-websocket)
- [Dariawan — Spring Boot + WebSocket With STOMP Tutorial](https://www.dariawan.com/tutorials/spring/spring-boot-websocket-stomp-tutorial/)

### 7. Docker fundamentals + Docker Compose
- [Docker and Kubernetes — Full Course for Beginners](https://www.youtube.com/watch?v=Wf2eSG3owoA)
- [Docker Containers and Kubernetes Fundamentals — Full Hands-On Course (freeCodeCamp)](https://www.youtube.com/watch?v=kTp5xUtcalw)

### 8. Sandboxed code execution (the judge worker)
- [Judge0 — official open-source sandboxed code execution system](https://github.com/judge0/judge0) — reference implementation for the class of problem the Judge Worker solves.
- [DEV Community — Building a secure/sandboxed environment for executing untrusted code](https://dev.to/narasimha1997/building-a-secure-sandboxed-environment-for-executing-untrusted-code-7e8)
- [piotrek-k.pl — Sandboxing using Docker: cheatsheet (network disable, read-only fs, memory/CPU limits, non-root user)](https://www.piotrek-k.pl/posts/sandboxing/)

The security story: Docker isn't perfect isolation (shared kernel with host), mitigated here via resource limits, no network, non-root, ephemeral containers, timeout kill (all implemented — see `docker/sandbox-python`, `docker/sandbox-java`, `DockerSandboxService`).

### 9. Kubernetes + Helm
- [Kubernetes Tutorial for Beginners — Full Course in 4 Hours](https://www.youtube.com/watch?v=X48VuDVv0do)
- [Complete Kubernetes Course — From Beginner to Pro](https://www.youtube.com/watch?v=2T86xAtR6Fo)
- [freeCodeCamp — What is a Helm Chart? Tutorial for Kubernetes Beginners](https://www.freecodecamp.org/news/what-is-a-helm-chart-tutorial-for-kubernetes-beginners/)
- [DevOpsCube — Helm Chart Tutorial: A Simple Guide for Beginners](https://devopscube.com/create-helm-chart/)

### 10. Observability: Prometheus + Grafana + Micrometer
- [Baeldung — Monitor a Spring Boot App Using Prometheus](https://www.baeldung.com/spring-boot-prometheus)
- [Java-Techie-jt — spring-boot-micrometer (GitHub, runnable example)](https://github.com/Java-Techie-jt/spring-boot-micrometer)

### 11. React + TypeScript + Monaco editor
- [`@monaco-editor/react` on npm](https://www.npmjs.com/package/@monaco-editor/react)
- [suren-atoyan/monaco-react (GitHub)](https://github.com/suren-atoyan/monaco-react)

### 12. ELO rating algorithm
- [GeeksforGeeks — Elo Rating Algorithm](https://www.geeksforgeeks.org/dsa/elo-rating-algorithm/)
- [Stanislav Stankovic — Elo Rating System (Medium, deeper derivation)](https://stanislav-stankovic.medium.com/elo-rating-system-6196cc59941e)

### 13. System design — the overarching reference
- [donnemartin/system-design-primer (GitHub)](https://github.com/donnemartin/system-design-primer) — the canonical resource this whole project is built to demonstrate coverage of: message queues, caching, load balancing, and the classic scale case studies.

### 14. Reference implementations (patterns, not wholesale reuse)
- [Avijit200318/Leetcode-Clone (GitHub) — Next.js + TS + Judge0 API + Monaco editor](https://github.com/Avijit200318/Leetcode-Clone)
- [ManiGhazaee/FireCode (GitHub) — full-stack LeetCode clone, React + Express + MongoDB](https://github.com/ManiGhazaee/FireCode)

---

## Part 2 — Build plan (blank folder → deployed product)

Mirrors the phase order in `goals.md`'s "Implementation phases" section. Each phase is independently demoable before the next begins.

### Phase 0 — Foundations (done)
Prerequisite reading: §1 (Spring Boot/microservices), §3 (auth), §7 (Docker/Compose)

Delivered:
1. Repo structure: one folder per service (`auth-service`, `gateway`, `user-service`, ...), shared `docker-compose.infra.yml` at root.
2. `docker-compose.infra.yml` bringing up Postgres, MongoDB, Redis, RabbitMQ as containers.
3. Auth Service: signup/login/verification/password reset, refresh-token rotation, Google OAuth, JWT issuance, transactional outbox for `user.events`.
4. API Gateway: Spring Cloud Gateway, JWT validation, routing, token-bucket rate limiting.
5. User/Profile Service: profile CRUD.
6. **Demo checkpoint (met):** register a user through the gateway, log in, get a JWT, hit a protected profile endpoint with it.

### Phase 1 — Core judge loop (done, single-player practice mode)
Prerequisite reading: §4 (RabbitMQ), §8 (sandboxed execution)

Delivered:
1. Problem Service: CRUD problems + test cases (Postgres).
2. Submission Service: accepts `{userId, problemId, code, language}`, stores metadata, publishes job to RabbitMQ `judge.jobs` queue.
3. Judge Worker: consumes from `judge.jobs`, pulls a Docker image per language, runs submitted code against test cases with resource limits (CPU/mem/timeout, no network, non-root, ephemeral container per run), writes result (verdict + per-test-case output) to MongoDB, publishes verdict back.
4. Submission Service exposes verdict to the client.
5. **Demo checkpoint (met):** submit a real solution to a real problem through the API, get back pass/fail per test case, end to end — no matchmaking or duel yet.

### Phase 2 — ELO + Matchmaking (done)
Prerequisite reading: §5 (Redis), §12 (ELO)

Delivered:
1. Matchmaking Service: `POST /matchmaking/queue/join` resolves the caller's ELO from User/Profile Service (never trusts a client-supplied value), publishes a join request to RabbitMQ's durable `matchmaking.join` queue.
2. A `@RabbitListener` consumer inserts the user into a Redis sorted set keyed by ELO plus a wait-start Hash - idempotent against redelivery.
3. A `@Scheduled` sweep (~1s) pairs waiting users oldest-first via an atomic Redis Lua script (`pair_match.lua`) that checks candidate freshness, scans for the nearest in-window opponent, and removes both in one step - safe under multiple horizontally-scaled instances without a distributed lock, verified by a Testcontainers-backed concurrency test (`PairingLuaScriptIT`).
4. On match: a random problem is assigned (new `GET /internal/problems/random` on Problem Service), a `Match` row is persisted via the transactional-outbox pattern, `match.created` published to the `match.events` topic exchange.
5. Max-wait expiry (120s) resolves the previously-open stale-join-request question; `GET /matchmaking/queue/status` and `DELETE /matchmaking/queue/leave` round out the API.
6. **Demo checkpoint (met):** two test clients join the queue, get paired within the expected window, Match record exists in Postgres — verified via API/logs, no live duel UI yet.

### Phase 3 — Real-time duel
Prerequisite reading: §6 (WebSocket/STOMP)

Plan:
1. WS Gateway Service: accepts client WebSocket connections, registers `connectionId` per user in Redis, subscribes to the topic exchange for `match.created`/`duel.progress`/`match.completed`.
2. Duel Service: owns match lifecycle, consumes judge verdicts tagged with `matchId`, updates progress %, republishes `duel.progress`.
3. Win condition + ELO update logic (standard ELO formula, K-factor ~32) on match completion, per `goals.md`'s duel flow.
4. WS Gateway pushes opponent's progress bar (not code, not submission count) to the other player's connection via the Redis `matchId -> connectionIds` lookup.
5. **Demo checkpoint:** two browser tabs, join queue, get matched, both see a live duel screen, opponent's progress bar updates in real time, winner declared, ELO updates.

### Phase 4 — Leaderboard + profile/stats
Plan:
1. Leaderboard Service consumes `match.completed` off the topic exchange, updates a Redis sorted set (`ZADD leaderboard <newElo> <userId>`).
2. Leaderboard API: top N, plus a given user's rank (`ZRANK`).
3. Profile Service: rating history, match history view (reads from Postgres match records).
4. **Demo checkpoint:** after a duel, both players' new ELO shows up correctly ranked on the leaderboard.

### Phase 5 — Frontend React SPA
Prerequisite reading: §11 (React/TS/Monaco). Built incrementally alongside Phases 0-4's API surface rather than as one final phase.

Status: landing page, login/signup (wired to Auth Service), and problem browser are implemented. Remaining:
1. Matchmaking queue screen ("searching for opponent...", cancel button).
2. Live duel view: opponent progress bar driven by the WS connection.
3. Leaderboard page, profile/stats page with rating history chart.

### Phase 6 — Kubernetes deployment
Prerequisite reading: §9 (K8s/Helm)

Plan:
1. Containerize every service (Dockerfile per service).
2. Write k8s manifests or a Helm chart per service (Deployment, Service, ConfigMap/Secret for config).
3. StatefulSets or Helm charts (e.g. Bitnami) for Postgres, MongoDB, Redis, RabbitMQ.
4. HPA (Horizontal Pod Autoscaler) on at least the Judge Worker (the natural bursty-load service) and API Gateway.
5. Deploy to local cluster (minikube or Docker Desktop's k8s), verify full flow works identically to docker-compose.
6. **Demo checkpoint:** `kubectl get pods` shows everything healthy, full user flow (signup → practice submit → duel → leaderboard) works against the k8s-deployed stack.

### Phase 7 — Observability
Prerequisite reading: §10 (Prometheus/Grafana/Micrometer)

Plan:
1. Add Spring Boot Actuator + Micrometer Prometheus registry to every service, exposing `/actuator/prometheus`.
2. Deploy Prometheus (e.g. via kube-prometheus-stack Helm chart) to scrape all services.
3. Grafana dashboards: request rate/latency/error rate per service, RabbitMQ queue depth, judge job processing latency, matchmaking wait time.
4. **Demo checkpoint:** generate load (a few concurrent duels/submissions), watch queue depth and latency move on a live Grafana dashboard.

**Explicitly deferred past v1** (per `goals.md`): distributed tracing (Jaeger/OpenTelemetry), centralized logging (ELK/Loki), Resilience4j circuit breakers, Kafka/event replay, plagiarism/anti-cheat detection.

---

## Remaining open design questions

Tracked in full in `goals.md`'s "Open questions / deferred decisions": exact data model for the Phase 3+ services (Duel/Match, Leaderboard), judge anti-cheat scope, testing strategy, CI/CD pipeline, and k8s manifest layout. Resolved incrementally as each phase lands, not as a blocking upfront pass. (Matchmaking join-request expiry was resolved in Phase 2.)
