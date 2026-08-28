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
2. `docker-compose.infra.yml` bringing up Postgres, Redis, and RabbitMQ as containers.
3. Auth Service: signup/login/verification/password reset, refresh-token rotation, JWT issuance, transactional outbox for `user.events`.
4. API Gateway: Spring Cloud Gateway, JWT validation, routing, token-bucket rate limiting.
5. User/Profile Service: profile CRUD.
6. **Demo checkpoint (met):** register a user through the gateway, log in, get a JWT, hit a protected profile endpoint with it.

### Phase 1 — Core judge loop (done, single-player practice mode)
Prerequisite reading: §4 (RabbitMQ), §8 (sandboxed execution)

Delivered:
1. Problem Service: CRUD problems + test cases (Postgres).
2. Submission Service: accepts `{userId, problemId, code, language}`, stores metadata, publishes job to RabbitMQ `judge.jobs` queue.
3. Judge Worker: consumes from `judge.jobs`, pulls a Docker image per language, runs submitted code against test cases with resource limits (CPU/mem/timeout, no network, non-root, ephemeral container per run), publishes the result (verdict + per-test-case output) back. Submission Service persists the result as JSONB in its own Postgres schema.
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

### Phase 3 — Real-time duel (done)
Prerequisite reading: §6 (WebSocket/STOMP)

Delivered:
1. `matchId`/`userId` threaded through Submission Service (`CreateSubmissionRequest`, `Submission` entity, `SubmissionResponse`, `JudgeJobCreatedEvent`) and Judge Worker (`SubmissionJudgedEvent`) — a gap caught mid-build: the verdict event originally carried no way to identify which player submitted it.
2. Duel Service: consumes `match.created` (persists a `Match` row keyed by the same matchId matchmaking-service generated) and `submission.judged` (filtered on `matchId != null`), updates per-player progress % (`max(existing, new)` so a worse resubmission never regresses the bar), win condition (first to 100%) and a `MatchTimeoutSweeper` (~1s, mirrors `MatchmakingSweepScheduler`) for time-limit expiry. The `IN_PROGRESS → COMPLETED` transition is guarded by JPA optimistic locking (`@Version`) against two near-simultaneous writers.
3. `EloCalculator`: standard logistic-curve ELO, K-factor 32, computed from each player's ELO-at-match-time (frozen on the `Match` row, never a live lookup). `duel.progress`/`match.completed` published via the same transactional-outbox pattern as `matchmaking-service`'s `MatchWriter`/`OutboxRelay`.
4. WS Gateway Service (standalone, direct-connect — not proxied through the API Gateway, which has no WebSocket-upgrade support): STOMP over WebSocket, JWT validated on the STOMP `CONNECT` frame (a `ChannelInterceptor`, not an HTTP filter — a browser can't set an `Authorization` header on a native WS upgrade). Horizontal-scaling fanout: one instance consumes each RabbitMQ event, re-publishes to a Redis Pub/Sub channel every instance subscribes to, and each instance's local Spring `SimpleBroker` only pushes to sessions it locally holds — first Redis Pub/Sub usage in this repo (every prior Redis use is request-response, not broadcast).
5. User Service: `MatchCompletedListener` (sole writer of ELO), `UserProfileService.applyMatchResult` guarded by a `profile.processed_match_completions(match_id)` dedup table — applying an ELO delta isn't naturally idempotent the way the outbox pattern's `published_at IS NULL` check is, so this needed an explicit guard against RabbitMQ's at-least-once redelivery.
6. Frontend: `/duel/[matchId]` page (`@stomp/stompjs` over native WebSocket, no SockJS fallback), initial state fetched via Duel Service's `GET /duels/{matchId}` (routed through the API Gateway) with WS carrying only live deltas after that. Matchmaking page's `MATCHED` state now routes here instead of the old placeholder.
7. **Demo checkpoint:** two browser tabs, join queue, get matched, both see a live duel screen, opponent's progress bar updates in real time, winner declared, ELO updates. Verified up through build/lint/typecheck for all five backend services and the frontend; not yet run live end-to-end in two browser tabs.

### Phase 4 — Leaderboard + profile/stats (done)
Plan:
1. Leaderboard Service consumes `match.completed` off the topic exchange, updating Redis sorted-set projections for global ELO, current ISO-week delta, and current calendar-quarter delta. Period updates use an atomic Lua check-then-increment script because RabbitMQ delivery is at-least-once.
2. Leaderboard API exposes public top-N, rank lookup, and rank-relative surrounding-player queries through the Gateway.
3. User Service stores transactional append-only ELO history; Duel Service exposes paginated match history. The frontend composes these independent reads on the authenticated profile page.
4. **Demo checkpoint:** after a duel, both players' new ELO shows up correctly ranked on the leaderboard, and the profile shows updated stats, rating history, and match history.

### Phase 5 — Ship-ready Frontend React SPA
Prerequisite reading: §11 (React/TS/Monaco). Built incrementally alongside Phases 0-4's API surface rather than as one final phase.

Delivered:
1. Product copy and metadata use clear, professional language: “Practice with purpose. Compete in real time.” User-facing copy uses match/challenge terminology while technical routes retain duel terminology.
2. Monaco editor is loaded client-only with Python and Java syntax highlighting, an application theme, responsive layout, read-only completed matches, and separate drafts per language so switching languages never discards work.
3. Protected screens share responsive navigation with active states, mobile menus, logout, skip links, visible focus rings, retryable failures, useful empty states, a branded 404 page, and reduced-motion-safe animation.
4. Auth Service's `user.created` event now includes the username. User Service projects that public handle into its own Postgres profile schema through a Flyway migration and exposes bounded `GET /users/public-identities?ids=...` reads.
5. Problem Service exposes bounded authenticated `GET /problems/summaries?ids=...` reads so profile history can show problem titles without coupling Duel Service to Problem Service data.
6. Leaderboard and profile pages enrich IDs with batch reads and use short-ID fallbacks when projections are temporarily unavailable.
7. Vitest + jsdom + React Testing Library cover leaderboard loading/success/empty/error/partial-identity states, authentication success/failure, problem loading/retry/unauthorized states, language switching, and judging submission lockout. Controller contract tests cover the bounded identity and problem-summary response shapes and request limits. `agent-browser` acceptance checks cover desktop and mobile navigation, redirects, screenshots, visible focus, overflow, reduced motion, and console errors.
8. **Verification evidence:** frontend lint, typecheck, 12 Vitest tests, and the offline production build pass locally. The browser pass confirmed the professional landing-page copy, responsive shell, mobile menu, protected redirects, branded 404, and no console errors.
9. **Demo checkpoint:** a user can sign in, browse a problem, edit a real Monaco buffer, switch languages without losing drafts, find a match, view public usernames on rankings, and inspect a readable profile history.

### Phase 6 — Kubernetes deployment (done)
Prerequisite reading: §9 (K8s/Helm)

Delivered:
1. Added pinned `postgresql`, `redis`, and `rabbitmq` Bitnami dependencies to the single `deploy/helm/leetduel` platform chart. PVCs are enabled by default, application images use `IfNotPresent`, and one namespace-scoped Secret is populated from ignored local input.
2. Added Dockerfiles for every application image, a standalone Next.js image, and a larger Judge Executor image containing JDK 21, Python 3.12, `timeout`, and the pinned `org.json` runtime library. Frontend production defaults compile API calls to same-origin `/api`; WebSocket code derives `ws:` or `wss:` from the browser host unless explicitly overridden.
3. Added Deployments, ClusterIP Services, probes, Ingress, Gateway and Dispatcher HPAs, Dispatcher RBAC, and an executor deny-egress NetworkPolicy. Kubernetes Service DNS replaces localhost only in the deployment layer; local defaults remain intact for Compose/development.
4. Replaced the Kubernetes judge path's host Docker socket with a Dispatcher/Executor boundary. The Dispatcher owns RabbitMQ and Kubernetes access, creates/reuses `judge-<submissionId>`, stores the original event in an immutable ConfigMap, reconciles completed Jobs, publishes the unchanged result event, then cleans up. The Executor has no broker credentials or ServiceAccount token and emits one framed result to logs after running one submission locally.
5. Preserved at-least-once semantics and terminal idempotency. RabbitMQ redelivery reuses deterministic resources; a Dispatcher crash before publish leaves work to reconciliation; a crash after publish may duplicate `submission.judged`, which Submission Service safely ignores after terminal status.
6. Added `scripts/bootstrap-minikube.ps1`, `scripts/build-minikube-images.ps1`, `scripts/deploy-minikube.ps1`, and `scripts/reset-minikube-data.ps1`. Validate with `helm dependency build deploy/helm/leetduel`, `helm lint deploy/helm/leetduel`, and `helm template ...`; run full browser acceptance once Docker/Minikube is available.
7. **Demo checkpoint:** use the scripts to deploy the Helm release, confirm every application rolls out with probes and every stateful dependency has a PVC, then exercise signup, login, practice judging, two-player matching, WebSocket progress, leaderboard changes, and profile history through the printed Ingress hostname. Kubernetes Job cold-start latency is an explicit local-demo trade-off for removing root-equivalent Docker socket access.

### Phase 7 — Intelligent practice loop (done)
Prerequisite reading: §4 (RabbitMQ), §5 (Redis), §10 (Prometheus/Grafana/Micrometer), plus the provider and vector references below.

Delivered:
1. Practice Intelligence Service owns the `practice` Postgres schema. It records every terminal practice attempt, maintains sticky solved state, derives weak tags from failures, and exposes progress/recommendation/explanation APIs through the Gateway.
2. Problem Service exposes a bounded internal metadata catalog and import path. The dataset script downloads a pinned `newfacade/LeetCodeDataset` `v0.3.1` compressed JSONL artifact, converts supported Python-style function cases into LeetDuel's JSON harness format, imports compatible records idempotently by source ID, preserves attribution, and creates a rejection report without storing reference solutions in user-facing data.
3. Postgres with pgvector stores 2048-dimensional NVIDIA document embeddings. NVIDIA passage/query embedding calls are retried with bounded backoff; tag/difficulty fallback keeps practice useful when the provider or key is unavailable. Redis caches recommendations as a derived projection.
4. Submission Service emits the additive practice-only completion event from its transactional outbox. The shared judged event now carries additive sample metadata, and public persisted results redact hidden expected/actual values. Practice consumers deduplicate by `submission_id` before updating progress or creating explanation jobs.
5. Hints are generated asynchronously and stored as validated structured JSON. Walkthroughs are user-triggered, provider failures are retryable, source code is retained only for the bounded explanation window, and no provider prompt/output is logged. Practice WebSocket messages notify the correct authenticated user through RabbitMQ, Redis Pub/Sub, and `/user/queue/practice`; REST remains the recovery source of truth.
6. **Demo checkpoint:** sign in, open `/problems`, see progress and recommendations, submit a practice solution, observe the terminal verdict without hidden output, receive a hint notification, and explicitly request a walkthrough.

Useful references:
- [NVIDIA Nemotron 3 Embed API](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-embed-1b-infer)
- [NVIDIA Nemotron 3 Super API](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer)
- [pgvector](https://github.com/pgvector/pgvector)
- [LeetCodeDataset](https://github.com/newfacade/LeetCodeDataset)

### Phase 8 — Observability
Prerequisite reading: §10 (Prometheus/Grafana/Micrometer)

Plan:
1. Add Spring Boot Actuator + Micrometer Prometheus registry to every service, exposing `/actuator/prometheus`.
2. Deploy Prometheus (e.g. via kube-prometheus-stack Helm chart) to scrape all services.
3. Grafana dashboards: request rate/latency/error rate per service, RabbitMQ queue depth, judge job processing latency, matchmaking wait time.
4. **Demo checkpoint:** generate load (a few concurrent duels/submissions), watch queue depth and latency move on a live Grafana dashboard.

**Explicitly deferred past v1** (per `goals.md`): distributed tracing (Jaeger/OpenTelemetry), centralized logging (ELK/Loki), Resilience4j circuit breakers, Kafka/event replay, plagiarism/anti-cheat detection.

---

## Remaining open design questions

Tracked in full in `goals.md`'s "Open questions / deferred decisions": CI/CD, observability, TLS, distributed tracing, KEDA, anti-cheat, and production multi-tenant hardening. Phase 6 resolves the chart layout, stateful dependency strategy, and Kubernetes judge boundary. (Matchmaking join-request expiry was resolved in Phase 2; Duel/Match's schema, WS auth, and cross-instance fanout were resolved in Phase 3.)
