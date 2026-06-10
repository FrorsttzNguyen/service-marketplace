# ADR 0001: PostgreSQL over MongoDB

## Status
Accepted

## Context
We need a primary database for the service marketplace. The two main contenders are PostgreSQL (relational) and MongoDB (document).

The domain has:
- Highly relational data (users → vendors → services → bookings → orders → payments)
- Financial transactions requiring ACID compliance
- Complex queries with joins (search services with vendor info, ratings, availability)
- Audit trail requirements

## Decision
Use **PostgreSQL** as the primary database.

## Consequences

### Positive
- **ACID transactions** — Critical for payment and booking operations. No eventual consistency surprises.
- **Relational integrity** — Foreign keys enforce data consistency naturally. No orphaned bookings or payments.
- **Rich query capabilities** — JOINs, window functions, CTEs, full-text search all built-in.
- **Industry standard** — Most backend job postings expect PostgreSQL experience. Directly transferable skill.
- **Mature ecosystem** — Spring Data JPA + Hibernate + Flyway work seamlessly with PostgreSQL.

### Negative
- **Schema rigidity** — Schema changes require migrations. However, this is a feature for learning — forces intentional database design.
- **Less flexible for unstructured data** — Service metadata might benefit from JSON columns. PostgreSQL supports `JSONB` as a compromise.
- **Vertical scaling bias** — Harder to shard than MongoDB. Not a concern for this project's scale.
