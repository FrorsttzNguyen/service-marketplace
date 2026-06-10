# Service Marketplace

> A multi-vendor service booking platform where vendors list services, customers discover and book them, and the platform manages payments, scheduling, and commissions.

## Why This Project Exists

This is a **learning vehicle** for core software engineering fundamentals:

- **Java & Spring Boot** — Backend framework mastery
- **OOP & Design Patterns** — State Machine, Strategy, Observer, Factory, Specification
- **Database Design** — ERD, normalization, indexes, migrations, constraints
- **System Design** — Architecture docs, ADRs, sequence diagrams, C4 models
- **Payment Integration** — Stripe API, webhooks, idempotency, escrow pattern
- **Production Practices** — Testing, CI/CD, Docker, logging, error handling

## Architecture

```
[See docs/architecture/ for full diagrams]
```

**Approach:** Modular Monolith — well-defined module boundaries with a documented path to microservices extraction if needed. See [ADR-0005](docs/adr/0005-modular-monolith-over-microservices.md).

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Security | Spring Security + JWT |
| Database | PostgreSQL |
| Cache | Redis |
| Payment | Stripe Java SDK |
| Migrations | Flyway |
| API Docs | Springdoc OpenAPI (Swagger) |
| Testing | JUnit 5, Mockito, TestContainers |
| Frontend | React / Next.js |
| Containerization | Docker + Docker Compose |
| CI/CD | GitHub Actions |

## Domain Overview

```
┌──────────────────────────────────────────────────┐
│                   ACTORS                         │
│  Customer  │  Vendor  │  Admin                   │
├──────────────────────────────────────────────────┤
│               CORE FLOWS                        │
│  Register → Browse → Book → Pay → Complete      │
│  Vendor Onboard → Create Service → Manage Book  │
│  Admin: Moderate → Analytics → Commission Payout │
└──────────────────────────────────────────────────┘
```

### Key Domain Concepts

| Concept | Description |
|---------|------------|
| **User** | Base account (Customer, Vendor, Admin roles) |
| **Vendor** | Service provider with profile, portfolio, ratings |
| **Service** | Offering with category, pricing, duration, availability |
| **Booking** | Reservation of a service at a specific time slot |
| **Order** | Payment wrapper around one or more bookings |
| **Payment** | Stripe-backed transaction with webhook confirmation |
| **Review** | Post-service rating and feedback by customer |

## Project Structure

```
service-marketplace/
├── docs/                           # Architecture & Design Documents
│   ├── architecture/               # Diagrams (C4, ERD, Sequence, State)
│   ├── adr/                        # Architecture Decision Records
│   ├── api/                        # OpenAPI spec
│   └── system-design.md            # Full system design writeup
├── src/
│   ├── main/java/com/hien/marketplace/
│   │   ├── config/                 # Spring configuration
│   │   ├── domain/                 # Domain models & business logic
│   │   │   ├── user/
│   │   │   ├── vendor/
│   │   │   ├── service/
│   │   │   ├── booking/
│   │   │   ├── order/
│   │   │   ├── payment/
│   │   │   ├── notification/
│   │   │   └── common/             # Shared: Money value object, Address, etc.
│   │   ├── application/            # Use cases, DTOs, mappers
│   │   ├── infrastructure/         # External integrations
│   │   └── interfaces/             # REST controllers
│   ├── main/resources/
│   │   └── db/migration/           # Flyway SQL migrations
│   └── test/java/                  # Tests mirroring main structure
├── frontend/                       # React / Next.js SPA
├── docker-compose.yml              # Local dev: app + PostgreSQL + Redis
└── README.md
```

## Getting Started

```bash
# Prerequisites: Java 17+, Docker, Node.js 18+

# Start infrastructure
docker-compose up -d postgres redis

# Run backend
./mvnw spring-boot:run

# Run frontend
cd frontend && npm install && npm run dev
```

## Documentation Index

- [System Design Document](docs/system-design.md)
- [Architecture Decision Records](docs/adr/)
- [API Specification](docs/api/openapi.yaml)
- [Database ERD](docs/architecture/erd/)
- [C4 Diagrams](docs/architecture/c4/)
- [State Machine Diagrams](docs/architecture/state-machines/)
- [Sequence Diagrams](docs/architecture/sequence-diagrams/)

## Learning Roadmap

See [docs/learning-roadmap.md](docs/learning-roadmap.md) for the phased approach to building this project while learning Java/Spring/OOP.

---

*Built as a learning project to demonstrate software engineering fundamentals: OOP, database design, system design, and payment integration.*
