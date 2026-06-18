# Service Marketplace

[![CI](https://github.com/FrorsttzNguyen/service-marketplace/actions/workflows/ci.yml/badge.svg)](https://github.com/FrorsttzNguyen/service-marketplace/actions/workflows/ci.yml)

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
| Language | Java 21 |
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

This is the **local dev environment** — a self-contained stack so you build and test WITHOUT touching
the live (prod) data. Two ways to run it:

```bash
# Prerequisites: Java 21, Docker, Node.js 18+

# --- Option A: full stack in Docker (app + Postgres + Redis), one command ---
cp .env.example .env          # fill POSTGRES_*, STRIPE_* (test keys), etc.
docker compose up --build     # backend on :8080, postgres :5433, redis :6379

# --- Option B: infra in Docker, backend from source (hot reload, SQL logs) ---
docker compose up -d postgres redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # backend on :8080

# --- Frontend (either option) ---
cd frontend && npm install
# .env.local → NEXT_PUBLIC_API_BASE_URL=http://localhost:8080  (local backend)
npm run dev                   # http://localhost:3000
```

CORS for `http://localhost:3000` is preconfigured for local dev (the `dev` profile and the compose
`app` service both allow it), so the local frontend talks to the local backend out of the box — no
prod config is touched.

### Environments (dev vs prod)

| Concern | Dev (local) | Prod |
|---------|-------------|------|
| Backend | `docker compose` / `mvnw … -Dspring-boot.run.profiles=dev` on `:8080` | Render (Docker, `prod` profile) |
| Postgres / Redis | Docker containers (ephemeral, `pgdata` volume) | Neon / Upstash (managed) |
| CORS allow-list | `localhost:3000` baked into the `dev` profile + compose | `APP_CORS_ALLOWED_ORIGINS` env (Vercel origins) |
| Stripe | test keys (`pk_test_`/`sk_test_`) | test keys (demo) |
| Frontend | `npm run dev` → `localhost:8080` | Vercel (`main` → production) |
| Secrets | local `.env` / `.env.local` (git-ignored) | Render/Vercel encrypted env stores |

Build and test against the **dev** stack; only `main` is promoted to prod. This keeps demo/prod data
clean (e.g. don't create throwaway accounts/reviews against the live DB).

## Deployment

The app is containerized (`Dockerfile`) and deployed on **Render** via the Blueprint at
[`render.yaml`](./render.yaml) (web service only). PostgreSQL and Redis are external managed
services so they survive app redeploys:

| Layer   | Provider | Notes |
|---------|----------|-------|
| App     | Render   | Docker runtime, free plan, health check on `/actuator/health` |
| Postgres| Neon     | Serverless Postgres; JDBC URL must end with `?sslmode=require` |
| Redis   | Upstash  | TLS endpoint; `REDIS_SSL=true` enables `spring.data.redis.ssl.enabled` |

### Deploy steps (Render Blueprint)

1. Create free-tier accounts on **Neon** (Postgres) and **Upstash** (Redis). Copy connection details.
2. In Render, **New → Blueprint** and point at this repo. Render reads `render.yaml`.
3. When prompted, fill the `sync: false` env vars in the Render dashboard (never committed):

   | Env var | Value source |
   |---------|--------------|
   | `SPRING_DATASOURCE_URL` | Neon connection string, append `?sslmode=require` |
   | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | Neon database credentials |
   | `SPRING_DATA_REDIS_HOST` / `_PORT` / `_PASSWORD` | Upstash TLS endpoint |
   | `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` | Stripe dashboard |
   | `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Optional — skip to disable admin bootstrap |
   | `JWT_SECRET` | Auto-generated by Render (`generateValue: true`); override with `openssl rand -base64 48` if desired |

4. Render builds the Docker image and starts the web service. First deploy runs Flyway migrations.
5. Verify `/actuator/health` returns `{"status":"UP"}` on the live URL.

### Live

- **Live URL:** https://marketplace-api-kehz.onrender.com (Render free — sleeps after ~15 min idle; first request may cold-start ~50s)
- **Health:** https://marketplace-api-kehz.onrender.com/actuator/health
- **Swagger / OpenAPI UI:** https://marketplace-api-kehz.onrender.com/swagger-ui/index.html
- **OpenAPI spec (committed):** [docs/api/openapi.yaml](docs/api/openapi.yaml)

> Secrets are injected at runtime via Render's encrypted env store. The Docker image contains no
> credentials, so it is safe to push to a public registry.

## Frontend (Phase 7)

The web client is a **Next.js App Router** application written in **TypeScript**, styled with
**Tailwind CSS**, with **TanStack Query** for server-state (catalog, bookings, orders, payments) and
**Stripe Elements** for the checkout card form. It is deployed on **Vercel** as a static + edge
runtime in front of the Spring backend above.

| Layer | Technology |
|-------|-----------|
| Framework | Next.js 14 (App Router) |
| Language | TypeScript (strict) |
| Styling | Tailwind CSS |
| Server state | TanStack Query |
| Payments | Stripe.js + Stripe React Elements (test mode) |
| API typing | Generated from `docs/api/openapi.yaml` via `openapi-typescript` |
| Hosting | Vercel |

The API client is **generated** from the committed OpenAPI spec, so every backend DTO the frontend
reads is type-checked against the contract — a backend field rename shows up as a compile error on
the next `npm run gen:api`. There is no hand-rolled API DTO layer.

### Frontend local dev

```bash
cd frontend
npm install

# Create a .env.local (git-ignored) with:
#   NEXT_PUBLIC_API_BASE_URL          backend base URL
#   NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY  Stripe pk_test_... key (test mode)
# A .env.example with these keys and notes ships in frontend/.

npm run gen:api   # regenerate the typed client from ../docs/api/openapi.yaml (no-op if spec unchanged)
npm run dev       # http://localhost:3000
```

Point `NEXT_PUBLIC_API_BASE_URL` at your local backend (`http://localhost:8080`) or the live Render
URL for end-to-end testing against the deployed API.

### Frontend scripts

| Script | Purpose |
|--------|---------|
| `npm run dev` | Next dev server on :3000 |
| `npm run build` | Production build |
| `npm run start` | Serve the production build |
| `npm run typecheck` | `tsc --noEmit` type check |
| `npm run gen:api` | Regenerate `lib/api/schema.d.ts` from the OpenAPI spec |
| `npm run lint` | `next lint` |

### Frontend deployment (Vercel)

1. Push the repo to GitHub.
2. In Vercel, **New Project → Import** the repo, set the **Root Directory** to `frontend`.
3. Set the same two environment variables in Vercel's project settings:
   - `NEXT_PUBLIC_API_BASE_URL` — backend base URL (e.g. the Render URL above)
   - `NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY` — Stripe **publishable** key (`pk_test_...` for test mode)
4. Deploy. Vercel runs `next build` automatically.

**Preview deploys (dev/prod split on the frontend):** connect the GitHub repo in Vercel (Project →
Settings → Git). Vercel then builds **every branch / PR as a Preview deployment** on its own
`*.vercel.app` URL, while `main` publishes to **Production**. The backend already allows
`https://*.vercel.app` in `APP_CORS_ALLOWED_ORIGINS`, so preview URLs hit the live API and work without
extra CORS config — review a change on a real URL before it reaches prod.

> Only `NEXT_PUBLIC_*` values belong in the frontend. The Stripe **secret** key and webhook secret
> live on the **backend** only.

### Live (frontend)

- **Live URL:** https://service-marketplace-alpha.vercel.app

![Service Marketplace — catalog](docs/images/frontend-catalog.png)

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
