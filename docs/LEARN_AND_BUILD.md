# LeetDuel — Learn, then Build

Companion to `goals.md`. This file has two parts: **what to learn** (grouped by topic, with resources), then **what to build** (phase by phase, blank folder to deployed product on Kubernetes).

Use this alongside `CLAUDE.md`'s explanation requirement — as you build each phase, ask for the "why" behind every decision so the resources below turn into interview-ready understanding, not copy-pasted code.

---

## Part 1 — What to Learn

Don't binge all of this up front. Learn each cluster right before the phase that needs it (see Part 2) — you retain more and see it applied immediately.

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

Focus while learning: difference between a **queue** (point-to-point, one consumer takes the message — this is your judge job queue and matchmaking join queue) and a **topic exchange** (fanout to multiple bound queues — this is your `match.created`/`duel.progress` broadcast to Duel Service + WS Gateway + Leaderboard Service). This distinction is exactly what you'll need to defend in an interview.

### 5. Redis (sorted sets, pub/sub)
- [GeeksforGeeks — Complete Guide to Redis Publish Subscribe](https://www.geeksforgeeks.org/system-design/redis-publish-subscribe/)
- [Redis Crash Course (Udemy) — sorted sets for ranking/leaderboards, pub/sub for real-time messaging](https://www.udemy.com/course/learn-redis-fast-the-complete-crash-course/)

Focus: `ZADD`/`ZRANGEBYSCORE`/`ZRANGEBYSCORE WITHSCORES` for the ELO matching window and leaderboard; `PUBLISH`/`SUBSCRIBE` for cross-instance WS fanout.

### 6. WebSocket + STOMP real-time
- [Spring Boot WebSocket Tutorial — Real-Time Chat App with STOMP & Java (YouTube)](https://www.youtube.com/watch?v=LF3Tn41j6Oc)
- [Toptal — Using Spring Boot for WebSocket Implementation with STOMP](https://www.toptal.com/developers/java/stomp-spring-boot-websocket)
- [Dariawan — Spring Boot + WebSocket With STOMP Tutorial](https://www.dariawan.com/tutorials/spring/spring-boot-websocket-stomp-tutorial/)

### 7. Docker fundamentals + Docker Compose
- [Docker and Kubernetes — Full Course for Beginners](https://www.youtube.com/watch?v=Wf2eSG3owoA)
- [Docker Containers and Kubernetes Fundamentals — Full Hands-On Course (freeCodeCamp)](https://www.youtube.com/watch?v=kTp5xUtcalw)

### 8. Sandboxed code execution (the judge worker)
- [Judge0 — official open-source sandboxed code execution system](https://github.com/judge0/judge0) — read this repo's architecture even if you don't reuse its code directly; it's the reference implementation for exactly what your Judge Worker does.
- [DEV Community — Building a secure/sandboxed environment for executing untrusted code](https://dev.to/narasimha1997/building-a-secure-sandboxed-environment-for-executing-untrusted-code-7e8)
- [piotrek-k.pl — Sandboxing using Docker: cheatsheet (network disable, read-only fs, memory/CPU limits, non-root user)](https://www.piotrek-k.pl/posts/sandboxing/)

Focus: this is your strongest security talking point. Learn *why* Docker isn't perfect isolation (shared kernel with host) and what you did to mitigate it (resource limits, no network, non-root, ephemeral containers, timeout kill) — interviewers ask this exact follow-up.

### 9. Kubernetes + Helm
- [Kubernetes Tutorial for Beginners — Full Course in 4 Hours](https://www.youtube.com/watch?v=X48VuDVv0do)
- [Complete Kubernetes Course — From Beginner to Pro](https://www.youtube.com/watch?v=2T86xAtR6Fo)
- [freeCodeCamp — What is a Helm Chart? Tutorial for Kubernetes Beginners](https://www.freecodecamp.org/news/what-is-a-helm-chart-tutorial-for-kubernetes-beginners/)
- [DevOpsCube — Helm Chart Tutorial: A Simple Guide for Beginners](https://devopscube.com/create-helm-chart/)

### 10. Observability: Prometheus + Grafana + Micrometer
- [Baeldung — Monitor a Spring Boot App Using Prometheus](https://www.baeldung.com/spring-boot-prometheus)
- [Java-Techie-jt — spring-boot-micrometer (GitHub, runnable example)](https://github.com/Java-Techie-jt/spring-boot-micrometer)

### 11. React + TypeScript + Monaco editor
- [`@monaco-editor/react` on npm — the library you'll actually use](https://www.npmjs.com/package/@monaco-editor/react)
- [suren-atoyan/monaco-react (GitHub) — same library's source, useful for advanced config](https://github.com/suren-atoyan/monaco-react)

### 12. ELO rating algorithm
- [GeeksforGeeks — Elo Rating Algorithm](https://www.geeksforgeeks.org/dsa/elo-rating-algorithm/)
- [Stanislav Stankovic — Elo Rating System (Medium, deeper derivation)](https://stanislav-stankovic.medium.com/elo-rating-system-6196cc59941e)

### 13. System design — the overarching reference
- [donnemartin/system-design-primer (GitHub)](https://github.com/donnemartin/system-design-primer) — the canonical resource this whole project is built to demonstrate. Read the sections on message queues, caching, load balancing, and the Pastebin/scale case studies before Phase 6-7; skim the rest early for vocabulary.

### 14. Reference implementations (read for patterns, don't copy wholesale)
- [Avijit200318/Leetcode-Clone (GitHub) — Next.js + TS + Judge0 API + Monaco editor](https://github.com/Avijit200318/Leetcode-Clone) — closest existing project to yours; useful for UI/problem-model ideas even though it's Next.js not Spring Boot.
- [ManiGhazaee/FireCode (GitHub) — full-stack LeetCode clone, React + Express + MongoDB](https://github.com/ManiGhazaee/FireCode)

---

## Part 2 — What to Build (blank folder → deployed product)

Matches the phased order in `goals.md`. Each phase: learn its cluster from Part 1 first, then build, then it should be independently demoable before moving on.

### Phase 0 — Foundations
**Learn first:** #1 (Spring Boot/microservices), #3 (auth), #7 (Docker/Compose)

**Build:**
1. Repo structure: one root, one folder per service (`auth-service`, `gateway`, `user-service`, ...), shared `docker-compose.yml` at root.
2. `docker-compose.yml` bringing up Postgres, MongoDB, Redis, RabbitMQ as containers (from `LEARN_AND_BUILD` #7 + your existing goals.md data strategy — no native installs needed for these).
3. Auth Service: signup/login, issues JWT (Spring Security).
4. API Gateway: Spring Cloud Gateway, validates JWT, routes to Auth + User services.
5. User/Profile Service: basic CRUD profile, starting ELO default (e.g. 1200).
6. **Demo checkpoint:** register a user through the gateway, log in, get a JWT, hit a protected profile endpoint with it.

### Phase 1 — Core judge loop (single-player practice mode)
**Learn first:** #4 (RabbitMQ), #8 (sandboxed execution)

**Build:**
1. Problem Service: CRUD problems + test cases (Postgres).
2. Submission Service: accepts `{userId, problemId, code, language}`, stores metadata, publishes job to RabbitMQ `judge.jobs` queue.
3. Judge Worker: consumes from `judge.jobs`, pulls a Docker image per language, runs submitted code against test cases with resource limits (CPU/mem/timeout, no network, non-root, ephemeral container per run), writes result (verdict + per-test-case output) to MongoDB, publishes verdict back.
4. Submission Service polls or is notified of verdict, exposes it to the client.
5. **Demo checkpoint:** submit a real solution to a real problem through the API, get back pass/fail per test case, end to end — no matchmaking or duel yet. This alone is a complete async-processing + sandboxing project.

### Phase 2 — ELO + Matchmaking
**Learn first:** #5 (Redis), #12 (ELO)

**Build:**
1. Matchmaking Service: `/queue/join` publishes `join_request` to RabbitMQ `matchmaking.join` queue.
2. Matchmaker consumer: inserts into Redis sorted set keyed by ELO, scans for a pair within the current (expanding) rating window on each insert.
3. On match found: remove both from Redis set, create Match record (Postgres), publish `match.created` to a RabbitMQ topic exchange.
4. **Demo checkpoint:** two test clients join the queue, get paired within the expected window, Match record exists in Postgres — no live duel UI yet, verify via API/logs.

### Phase 3 — Real-time duel
**Learn first:** #6 (WebSocket/STOMP)

**Build:**
1. WS Gateway Service: accepts client WebSocket connections, registers `connectionId` per user in Redis, subscribes to the topic exchange for `match.created`/`duel.progress`/`match.completed`.
2. Duel Service: owns match lifecycle, consumes judge verdicts tagged with `matchId`, updates progress %, republishes `duel.progress`.
3. Win condition + ELO update logic (standard ELO formula, K-factor ~32) on match completion, per your goals.md duel flow.
4. WS Gateway pushes opponent's progress bar (not code, not submission count) to the other player's connection via the Redis `matchId -> connectionIds` lookup.
5. **Demo checkpoint:** two browser tabs, join queue, get matched, both see a live duel screen, opponent's progress bar updates in real time, winner declared, ELO updates.

### Phase 4 — Leaderboard + profile/stats
**Build:**
1. Leaderboard Service consumes `match.completed` off the topic exchange, updates a Redis sorted set (`ZADD leaderboard <newElo> <userId>`).
2. Leaderboard API: top N, plus a given user's rank (`ZRANK`).
3. Profile Service: rating history, match history view (reads from Postgres match records).
4. **Demo checkpoint:** after a duel, both players' new ELO shows up correctly ranked on the leaderboard.

### Phase 5 — Frontend React SPA
**Learn first:** #11 (React/TS/Monaco). Build this incrementally alongside Phases 0-4's API surface rather than as one final phase — each backend demo checkpoint above should really be checked through the actual UI once it exists.

**Build:**
1. Auth pages (login/signup), JWT stored client-side, attached to API calls.
2. Problem browser + problem detail page with Monaco editor, submit button hitting Submission Service.
3. Matchmaking queue screen ("searching for opponent...", cancel button).
4. Live duel view: opponent progress bar driven by the WS connection.
5. Leaderboard page, profile/stats page with rating history chart.

### Phase 6 — Kubernetes deployment
**Learn first:** #9 (K8s/Helm)

**Build:**
1. Containerize every service (Dockerfile per service, if not already from Phase 0-4 dev containers).
2. Write k8s manifests or a Helm chart per service (Deployment, Service, ConfigMap/Secret for config).
3. StatefulSets or Helm charts (e.g. Bitnami) for Postgres, MongoDB, Redis, RabbitMQ.
4. HPA (Horizontal Pod Autoscaler) on at least the Judge Worker (the natural bursty-load service) and API Gateway.
5. Deploy to local cluster (minikube or Docker Desktop's k8s), verify full flow works identically to docker-compose.
6. **Demo checkpoint:** `kubectl get pods` shows everything healthy, full user flow (signup → practice submit → duel → leaderboard) works against the k8s-deployed stack.

### Phase 7 — Observability
**Learn first:** #10 (Prometheus/Grafana/Micrometer)

**Build:**
1. Add Spring Boot Actuator + Micrometer Prometheus registry to every service, exposing `/actuator/prometheus`.
2. Deploy Prometheus (e.g. via kube-prometheus-stack Helm chart) to scrape all services.
3. Grafana dashboards: request rate/latency/error rate per service, RabbitMQ queue depth, judge job processing latency, matchmaking wait time.
4. **Demo checkpoint:** generate load (a few concurrent duels/submissions), watch queue depth and latency move on a live Grafana dashboard.

**Explicitly deferred past v1** (per goals.md — don't build these unless you decide to extend scope later): distributed tracing (Jaeger/OpenTelemetry), centralized logging (ELK/Loki), Resilience4j circuit breakers/rate limiting, Kafka/event replay, plagiarism/anti-cheat detection.

---

## Still open before this can become a full implementation plan

Per `goals.md`'s "Open / not yet decided" section — these need a brainstorming pass before Phase 0 coding starts: exact data model per service, judge sandbox security specifics (language runtimes/base images/exact limits), testing strategy, CI/CD, exact k8s manifest layout, and matchmaking join-request expiry handling.
