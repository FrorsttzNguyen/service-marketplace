# ADR 0005: Modular Monolith over Microservices

## Status
Accepted

## Context
We need to choose an architectural style for the service marketplace. Options:
- **Microservices**: Each domain (user, vendor, booking, payment) as a separate service
- **Modular monolith**: Single deployable with well-defined module boundaries
- **Big ball of mud**: No structure (rejected immediately)

This is a portfolio project built by one developer learning Spring Boot. Current scale: zero users.

## Decision
Build a **modular monolith** with clear module boundaries that could be extracted into microservices later.

## Consequences

### Positive
- **Simplicity** — One codebase, one deployment, one database. No distributed system complexity to debug.
- **Faster development** — No inter-service communication, no API versioning between services, no distributed transactions.
- **Demonstrates better judgment** — Senior engineers respect "right-sized architecture" over "resume-driven architecture." A single developer building 5 microservices for 0 users signals poor judgment.
- **Module boundaries still visible** — Package structure shows where seams are. Each domain module has its own entities, repositories, and services.
- **Extraction path documented** — The module interfaces are designed so that any module (e.g., notification, payment) could be extracted into a standalone service communicating via REST or message queue.

### Negative
- **Can't demonstrate Kubernetes/Docker Compose multi-service** — However, Docker Compose is still used for PostgreSQL + Redis.
- **Single point of failure** — One process goes down, everything goes down. Not a concern at this scale.
- **Harder to use different tech stacks per module** — All modules use Java/Spring. Acceptable tradeoff.

### When to Extract
Document for system design discussion:
1. **Notification service** — First candidate. Different deployment cadence, can use different tech (e.g., Node.js for WebSocket).
2. **Payment service** — Second candidate. Critical path, benefits from independent scaling and deployment.
3. **Search service** — If using Elasticsearch, could be a standalone service with its own data sync.
