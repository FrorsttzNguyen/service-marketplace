# CV Strategy & Career Orientation

## Current State (as of June 2026)

### CV Profile
- **Education:** VNU HCM — IT, GPA 3.2, graduating March 2026
- **Strength:** AI/ML experience (research lab, publication, Stanford ML cert)
- **Gap:** Zero evidence of Java/Spring Boot backend engineering

### Current CV Projects Problem
| Project | Problem |
|---------|---------|
| "Agentic AI Workflow for ITSM" | Identical to SCC Vietnam job description |
| "Tool-Using AI Chatbot for Rental" | Identical to Grab Holdings job description |

→ Hiring managers see this and think: "No side projects, just rewrote work."

## Target State

### CV After This Project
```
NGUYEN PHUONG NHAT HIEN

EXPERIENCE
├── SCC Vietnam — AI Engineer Intern (Dec 2025 - Jun 2026)
│   → Shows: Agentic AI, RAG, LangGraph, Azure, Production safety
│
└── Grab Holdings — Fullstack Developer Trainee (Apr 2025 - Jun 2025)
    → Shows: ReactJS, FastAPI, AI chatbot, Tool-use workflows

PROJECTS
├── Service Marketplace — Multi-Vendor Booking Platform ★ FLAGSHIP
│   → Shows: Java, Spring Boot, PostgreSQL, Redis, Stripe Payment,
│            OOP Design Patterns, Database Design, System Design
│            Concurrency handling, API design, Docker
│   → GitHub: Full repo with architecture docs, ADRs, diagrams
│
└── Barca Mobile App — iOS App (when launched on App Store)
    → Shows: Mobile development, product launch, App Store deployment
```

### What This Signals to Hiring Managers
1. **Breadth** — Not just AI. Can do backend, mobile, and AI.
2. **Depth** — The Service Marketplace shows serious backend engineering, not surface-level.
3. **Maturity** — Architecture docs, ADRs, system design writeup = thinks like a senior engineer.
4. **Real-world skills** — Payment integration, concurrency, caching = not academic.
5. **Self-driven** — Built a complex project while learning, shows initiative.

## How to Describe on CV

### Project Entry (Draft — refine when complete)
```
Service Marketplace — Multi-Vendor Booking Platform
Backend Engineering Project | June 2026
• Designed and built a multi-vendor service booking platform using Spring Boot,
  PostgreSQL, and Redis with Stripe payment integration.
• Implemented domain-driven design with 12+ entities, state machines for
  booking/order lifecycle, and design patterns (Strategy, Observer, Factory).
• Prevented double-booking with optimistic locking and database constraints;
  ensured payment safety with idempotent Stripe webhook processing.
• Built caching layer with Redis (cache-aside pattern) for service catalog,
  reducing query latency; added rate limiting on authentication endpoints.
• Documented system architecture with C4 diagrams, 6 Architecture Decision
  Records, sequence diagrams, and full OpenAPI specification.
```

### Skills Section Update
Move Java from "list" to "demonstrated":
```
Before: Languages & Frameworks: Python, TypeScript, JavaScript, Java, ...
After:  Languages & Frameworks: Java (Spring Boot), Python, TypeScript,
        JavaScript, FastAPI, React, Next.js, Node.js
```

Add if not present:
```
Backend: Spring Boot, Spring Security, Spring Data JPA, PostgreSQL, Redis,
         Stripe API, Flyway, Docker, REST API Design
```

## Learning Path Alignment

| Learning | Project Phase | What It Demonstrates |
|----------|--------------|---------------------|
| Java basics | Phase 0 | Can set up and run a Spring Boot project |
| OOP | Phase 1 | Domain model with value objects, state machines, composition |
| Database Design | Phase 1 | ERD, normalization, migrations, indexes, constraints |
| Spring Boot | Phase 2-3 | REST API, Security, Transactions, JPA |
| Design Patterns | Phase 3 | Strategy, Observer, Factory, State, Specification |
| System Design | Phase 7 | C4 diagrams, ADRs, sequence diagrams, architecture docs |

## Career Direction Options (after this project)

### Option A: Backend Engineer (Java/Spring)
This project directly qualifies. Target companies: Vietnamese tech companies using Java stack (banks, enterprises, Shopee, Tiki, FPT).

### Option B: Full-Stack Engineer
Combine this project (backend) + Barca app (mobile) + existing React skills. Very versatile profile.

### Option C: AI Engineer (current path)
Continue on current trajectory. The backend project adds credibility for "ML engineer who can also build production systems."

### Option D: Backend + AI Hybrid (recommended differentiator)
Most AI engineers can't build proper backend systems. Most backend engineers can't do AI. Being good at BOTH is rare and valuable. The CV would show: AI experience (jobs) + Backend engineering (this project).

## Timeline

| Milestone | Target | Notes |
|-----------|--------|-------|
| Phase 0-1 done | After Java/OOP study complete | Foundation is critical |
| Phase 2-3 done | After Spring Boot study | Core engineering |
| Phase 4 done | After learning payment integration | Real-world complexity |
| Phase 5 done | After learning caching | Performance thinking |
| Phase 6-7 done | When ready to polish | Portfolio-ready |
| CV update | After Phase 7 | Rewrite project section |
| Start applying | After CV update | Backend + AI hybrid roles |

## Key Metrics for Success

- [ ] GitHub repo has 50+ commits with clean history
- [ ] System design doc is interview-ready
- [ ] Can explain every ADR in an interview
- [ ] Test coverage >= 80% on service layer
- [ ] Can demo the full booking + payment flow
- [ ] README makes a strong first impression
