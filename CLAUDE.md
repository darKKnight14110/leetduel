# LeetDuel — Claude instructions

Purpose of this project: resume/interview prep for top backend/systems roles. See `goals.md` for full design.

## Explanation requirement (overrides default terseness for this project)

Every time you touch design, code, schema, or infra in this repo, explain the reasoning — not just what, but why — as if prepping the user to defend it in a systems-design interview. Specifically:

- **DB design**: when creating/changing a schema, state why this table/column/type/index exists, why this DB (Postgres vs Mongo vs Redis) was chosen for this data, normalization tradeoffs, what query pattern the index serves.
- **Service boundaries**: when adding/changing a service, state why this responsibility lives here and not elsewhere, what it owns, what happens if it goes down.
- **Message flow**: when touching RabbitMQ/Redis pub-sub, state delivery guarantees (at-least-once vs at-most-once), what happens on consumer crash/restart, why queue vs exchange type chosen.
- **Algorithms**: when implementing ELO/matchmaking/win-condition logic, state the complexity, edge cases, and alternative approaches considered.
- **Trade-offs**: always name what was given up (e.g. "no Kafka replay", "no distributed tracing yet") and why that's acceptable at this scope — this is exactly the kind of question interviewers ask.

Don't pad unrelated responses with this — only when the topic is a genuine design/implementation decision worth defending.

## Do not silently simplify

If asked to build something and a simpler shortcut exists that would undercut the interview story (e.g. skipping Docker sandboxing, skipping the Redis connection registry for WS), flag the shortcut and the trade-off explicitly rather than just taking it.
