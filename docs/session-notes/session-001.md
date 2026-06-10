# Session Handoff Note — Session 001

**Date:** 2026-06-10
**Phase:** Pre-Phase 0 → Phase 0 Ready
**Status:** Project scaffolded, GitHub clean, ready to start coding

## What Was Done
- Analyzed Hien's CV → identified 2 capstone projects as job rewrites, zero Java/Spring evidence
- Surveyed 3 project concepts → chose Service Marketplace (multi-vendor booking)
- Created project scaffold with full documentation (see Key Files below)
- Created GitHub repo: https://github.com/FrorsttzNguyen/service-marketplace
- Rewrote git history to remove all AI-related files (CLAUDE.md, .claudeignore, etc.)
- Setup `.git/info/exclude` for local-only AI file ignores (never appear on GitHub)
- Verified: commits show FrorsttzNguyen identity, no Claude logo, no AI traces on GitHub
- Evaluated Barca agent team setup → decided NOT to use agent teams (learning project, sequential phases)
- Created lightweight verifier agent only (`.claude/agents/verifier.md`)
- Updated CLAUDE.md with agent setup section

## Key Decisions Made
| Decision | Choice | Why |
|----------|--------|-----|
| Project concept | Service Marketplace | Covers OOP + DB + System Design + Payment, not tutorial-like |
| Architecture | Modular monolith | Right-sized for one developer, shows good judgment |
| Database | PostgreSQL | ACID, relational integrity, industry standard |
| Payment | Stripe | Industry standard, webhook idempotency is a great learning topic |
| Caching | Redis cache-aside | Demonstrates caching strategy |
| CV strategy | Replace both capstones with this + Barca app | Fixes AI-heavy imbalance |
| Agent teams | NO | Learning project — Hien must understand every line |
| AI file visibility | Hidden from GitHub | `.git/info/exclude` for local-only ignores |

## Key Files (on GitHub)
| File | Purpose |
|------|---------|
| `README.md` | Project overview, tech stack, domain, structure |
| `docs/system-design.md` | Full system design (architecture, data model, flows, caching, security) |
| `docs/learning-roadmap.md` | 8-phase learning plan with learn/build/verify per phase |
| `docs/todo.md` | Detailed checklist for all 8 phases (checkable) |
| `docs/git-flow.md` | Git branch strategy, PR process, gh CLI reference |
| `docs/cv-strategy.md` | CV plan + career orientation |
| `docs/adr/0001-0006` | 6 Architecture Decision Records |

## Key Files (local only, not on GitHub)
| File | Purpose |
|------|---------|
| `CLAUDE.md` | AI assistant rules (git, code style, architecture) |
| `.claude/agents/verifier.md` | Lightweight read-only verification agent |
| `.git/info/exclude` | Local-only ignore for AI files |
| `docs/.ai-generated-note.md` | Tracks initially AI-generated files for transparency |

## Where to Pick Up Next Session
1. **Read** `docs/todo.md` → Phase 0 section
2. **Check** Hien's Java learning progress — has he learned Java basics yet?
3. **If ready:** Create branch `feat/phase0-foundation`, initialize Spring Boot project
4. **If not ready:** Continue Java learning, come back when comfortable with basics

## Phase 0 Plan (Ready to Execute)
**Branch:** `feat/phase0-foundation`
**Steps:**
1. Create Spring Boot project (Spring Initializr or Maven archetype)
   - Dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway, Validation, Lombok, Spring Boot DevTools
2. Create `docker-compose.yml` (PostgreSQL 16 + Redis 7)
3. Configure `application.yml` (profiles: dev, test)
4. Create health endpoint `GET /api/health`
5. Verify: app starts, docker services run, health endpoint returns 200
6. Commit, push, create PR to main

## Important Notes
- Hien is learning Java → OOP → Spring Boot → System Design concurrently
- He's also working on Barca Mobile App (separate project, targeting App Store launch)
- Don't rush. This is a learning project. Depth > speed.
- AI features can be added later as bonus, but core must be pure software engineering
- No Co-Authored-By in any commit — Hien's identity only
- All AI-related files hidden from GitHub via `.git/info/exclude`

## Git State
- GitHub repo: https://github.com/FrorsttzNguyen/service-marketplace
- 1 clean commit on `main` (no AI files)
- Commit author: FrorsttzNguyen (avatar verified, no Claude logo)
- SSH protocol configured for git operations
- `.git/info/exclude` hides: CLAUDE.md, .claude/, .claudeignore, docs/.ai-generated-note.md
