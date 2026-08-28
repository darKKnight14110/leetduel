# LeetDuel documentation

This directory separates the product's architecture decisions, learning path, and operational evidence.

| Document | Purpose |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Service ownership, request/event paths, consistency, and scaling boundaries |
| [`PERFORMANCE.md`](PERFORMANCE.md) | Reproducible capacity model, pool budget, assumptions, and honest limits |
| [`goals.md`](goals.md) | Detailed system design decisions and implementation phases |
| [`LEARN_AND_BUILD.md`](LEARN_AND_BUILD.md) | Interview-oriented learning path and build history |

The project uses PostgreSQL as the durable source of truth, Redis for derived/hot state, RabbitMQ for at-least-once asynchronous delivery, and Kubernetes Jobs for isolated code execution. MongoDB is not part of the current architecture.
