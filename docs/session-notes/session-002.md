# Session Handoff Note — Session 002

**Date:** 2026-06-11
**Phase:** Phase 0 COMPLETE → Phase 1 Ready
**Status:** Phase 0 merged to main, learning doc written, ready to plan Phase 1

## What Was Done This Session
- Implemented Phase 0: Spring Boot 3.5.0 + Java 21 + Maven
- Fixed port conflict (Homebrew PostgreSQL on 5432, Docker uses 5433)
- Docker Compose: PostgreSQL 16 Alpine (256MB) + Redis 7 Alpine (64MB)
- Application profiles: dev (Docker) + test (H2 in-memory)
- Health endpoints: custom `/api/health` + Actuator `/actuator/health`
- Spring Security: stateless, permit all (Phase 2 will add JWT)
- PR #1 created and merged (squash)
- Phase 0 review written in `docs/todo.md`
- Comprehensive learning doc: `docs/phase0-learning-doc.md`
- Updated CLAUDE.md: added learning model section (vibe-coding, explain every line)

## Key Files Created This Session
| File | Purpose |
|------|---------|
| `pom.xml` | Maven build with all dependencies |
| `mvnw` / `mvnw.cmd` / `.mvn/` | Maven wrapper (no global Maven needed) |
| `docker-compose.yml` | PostgreSQL + Redis (alpine, memory limits) |
| `.env.example` | Environment variable docs |
| `src/.../ServiceMarketplaceApplication.java` | Spring Boot main class |
| `src/.../config/SecurityConfig.java` | Security config (stateless, permit all) |
| `src/.../interfaces/rest/HealthController.java` | Custom health endpoint |
| `src/.../application.yml` | Common config |
| `src/.../application-dev.yml` | Dev profile (Docker PostgreSQL:5433, Redis) |
| `src/.../application-test.yml` | Test profile (H2, no Docker) |
| `docs/phase0-learning-doc.md` | Comprehensive learning document for Phase 0 |
| `docs/todo.md` | Updated with Phase 0 review |

## Infrastructure Notes
- **Homebrew PostgreSQL** running on port 5432 (Hien's local)
- **Docker PostgreSQL** mapped to port 5433 (avoid conflict)
- **Docker Redis** on port 6379 (no conflict)
- Docker containers: `marketplace-postgres`, `marketplace-redis`
- `.git/info/exclude` hides AI files locally

## Phase 1 Plan (NOT YET IMPLEMENTED)
**Branch:** `feat/phase1-domain-model`
**Goal:** Complete domain model + database schema

Phase 1 will create:
1. ERD diagram (Mermaid) with all 12+ entities
2. Flyway migrations V1-V6 for all tables
3. Domain entities: User, Vendor, Service, Booking, Order, Payment, Review, Notification
4. Value objects: Money, Address, TimeSlot, PhoneNumber
5. State machines: BookingStatus, OrderStatus, PaymentStatus
6. Spring Data JPA repositories for all entities

Key OOP concepts to demonstrate:
- Composition over inheritance (Vendor has-a User)
- State machine pattern (Booking lifecycle)
- Strategy pattern (PricingType: fixed, hourly, variable)
- Value objects (Money prevents negative amounts)

## Where to Pick Up Next Session
1. Read `docs/phase0-learning-doc.md` if not yet reviewed
2. Read `docs/todo.md` Phase 1 section
3. Phase 1 plan should be ready (from current session's planning)
4. Create branch `feat/phase1-domain-model`
5. Start with ERD design, then Flyway migrations, then entities

## Important Reminders
- No Co-Authored-By in commits
- AI files hidden via `.git/info/exclude`
- Docker uses port 5433 (not 5432) for PostgreSQL
- `.env` is gitignored, never commit credentials
