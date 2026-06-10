# Session Handoff Note — Session 001

**Date:** 2026-06-10
**Phase:** Pre-Phase 0 (Project Setup)
**Status:** Project scaffolded, not yet coding

## What Was Done
- Analyzed Hien's CV and identified issues (2 projects = rewrite of jobs, zero Java evidence)
- Chose Service Marketplace concept (Option 1 of 3 surveyed)
- Created project folder with full structure at `/Users/hiennguyen/Project/service-marketplace/`
- Created documentation:
  - `README.md` — Project overview, tech stack, domain, structure
  - `CLAUDE.md` — Project rules for AI assistant (includes git/gh rules, no co-author)
  - `docs/system-design.md` — Full system design writeup
  - `docs/learning-roadmap.md` — 8-phase learning plan
  - `docs/todo.md` — Detailed checklist for all phases
  - `docs/git-flow.md` — Git branch strategy, PR process, gh CLI reference
  - `docs/cv-strategy.md` — How this project fits into CV and career direction
  - `docs/adr/0001-0006` — 6 Architecture Decision Records
  - `docs/.ai-generated-note.md` — Marks which files were AI-assisted initially
  - `.claudeignore` — Separates AI working files from developer code
- Initialized git repo, created GitHub repo, pushed initial commit
- Saved project memory to Claude memory system

## Where to Pick Up Next Session
1. **Read** `docs/todo.md` → start Phase 0
2. **Initialize** Spring Boot project via Spring Initializr
3. **Setup** docker-compose.yml (PostgreSQL + Redis)
4. **Push** to GitHub

## Key Decisions Made
- **Project concept:** Multi-vendor service marketplace (not event ticketing, not fintech)
- **Architecture:** Modular monolith (not microservices)
- **Database:** PostgreSQL (not MongoDB)
- **Payment:** Stripe
- **Caching:** Redis (cache-aside pattern)
- **CV strategy:** Replace both capstone projects with this one + Barca app

## Files Created This Session
```
README.md
CLAUDE.md
.gitignore
.claudeignore
docs/todo.md
docs/git-flow.md
docs/cv-strategy.md
docs/learning-roadmap.md
docs/system-design.md
docs/.ai-generated-note.md
docs/adr/0001-postgresql-over-mongodb.md
docs/adr/0002-redis-cache-aside.md
docs/adr/0003-optimistic-locking-for-booking-conflicts.md
docs/adr/0004-stripe-webhook-idempotency.md
docs/adr/0005-modular-monolith-over-microservices.md
docs/adr/0006-jwt-over-session.md
docs/session-notes/session-001.md
```

## Important Notes
- Hien is learning Java → OOP → Spring Boot → System Design concurrently
- He's also working on Barca Mobile App (separate project, targeting App Store)
- Don't rush. This is a learning project. Depth > speed.
- AI features can be added later as bonus, but core must be pure software engineering
- No Co-Authored-By in any commit — Hien's identity only

## Git Verification
- GitHub repo: https://github.com/FrorsttzNguyen/service-marketplace
- Commit author: FrorsttzNguyen (correct, no Claude logo)
- SSH protocol configured for git operations
