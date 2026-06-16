# Service Marketplace — Project Rules

## Project Context

This is a **learning vehicle** for Java/Spring Boot backend engineering. The owner (Hien) is learning:
- Java fundamentals → OOP → Spring Boot → System Design
- Building toward a portfolio-worthy GitHub project for CV

## Architecture Decisions

- **Modular monolith** — NOT microservices. Well-defined module boundaries, documented path to extraction.
- **Layered architecture**: `interfaces` (REST) → `application` (use cases) → `domain` (models + logic) → `infrastructure` (external)
- **PostgreSQL** for persistence, **Redis** for caching, **Stripe** for payments
- **Flyway** for database migrations — never `ddl-auto=create-drop`

## Code Style

- **Domain-rich design** — business logic lives in domain layer, not in services/controllers
- **Value objects** for Money, Address, TimeSlot — never use primitives for domain concepts
- **State machines** for Booking and Order lifecycle (enum-based with explicit transitions)
- **Design patterns where they add clarity**, not everywhere:
  - Strategy: pricing rules, commission calculation
  - State: booking/order status transitions
  - Observer: notification events
  - Factory: payment method creation
  - Specification: search/filter rules

## Phase Evaluation Rules (MANDATORY)

### Scoring Criteria

Each phase is scored out of 10 with weighted criteria:

| Criteria | Weight | Description |
|----------|--------|-------------|
| Learning Docs | 30% | Quality of teaching material, diagrams, "why" explanations |
| Code Quality | 30% | Architecture, patterns, conventions, documentation |
| Test Coverage | 20% | Unit/integration tests, all passing |
| Concept Mastery | 20% | Hien can explain concepts, not just copy code |

### Honest Assessment Rules

**CRITICAL: Be truthful, not flattering.**

1. **If score < 8/10 for any criteria**: Explain clearly what's missing, what needs improvement
2. **If expectation not met**: State explicitly WHY it falls short
3. **No inflation**: Don't give 9/10 if work is merely "okay"
4. **Evidence required**: Every score must be backed by specific examples from code/docs
5. **Gaps documented**: All missing items must be listed with phase fix plan

### Scoring Guide Per Criteria

**Learning Docs (30%)**
- 9-10: Comprehensive, diagrams, "why" sections, progressive depth
- 7-8: Good but missing some elements (e.g., no sequence diagrams)
- 5-6: Basic coverage, lacks depth or visual aids
- Below 5: Incomplete, major gaps

**Code Quality (30%)**
- 9-10: Production-ready, follows all conventions, clean architecture
- 7-8: Minor issues documented (N+1 queries, incomplete features)
- 5-6: Works but needs refactoring
- Below 5: Architectural violations, security issues

**Test Coverage (20%)**
- 9-10: 80%+ coverage, all tests passing
- 7-8: Good coverage, deferred tests documented
- 5-6: Basic tests, significant gaps
- Below 5: No tests or failing tests

**Concept Mastery (20%)**
- 9-10: Hien can explain and teach back
- 7-8: Good understanding, minor gaps
- 5-6: Surface understanding, needs reinforcement
- Below 5: Concepts not understood

### Evaluation Output Format

```
┌─────────────────┬────────┬────────┬──────────┐
│    Criteria     │ Weight │ Score  │ Weighted │
├─────────────────┼────────┼────────┼──────────┤
│ Learning Docs   │ 30%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Code Quality    │ 30%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Test Coverage   │ 20%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ Concept Mastery │ 20%    │ X/10   │ X.XX     │
├─────────────────┼────────┼────────┼──────────┤
│ TOTAL           │ 100%   │        │ X.XX/10  │
└─────────────────┴────────┴────────┴──────────┘
```

### Historical Scores (Public Record)

| Phase | Docs | Code | Tests | Mastery | Total | Notes |
|-------|------|------|-------|---------|-------|-------|
| Phase 0 | 8.5 | 8.0 | 8.0 | 9.0 | **8.25** | Foundation complete |
| Phase 1 | 9.5 | 9.5 | 9.5 | 9.5 | **9.5** | Domain model excellent |
| Phase 2 | 9.5 | 9.0 | 8.5 | 9.0 | **9.05** | Tests deferred to Phase 3 |
| Phase 3 | 9.5 | 8.5 | 9.0 | 9.0 | **9.00** | Business logic complete |
| Phase 4 | 9.5 | 9.0 | 9.0 | 9.0 | **9.15** | Payment integration complete |
| Phase 5 | 8.0 | 8.5 | 7.5 | 8.0 | **8.05** | Caching + rate limit; Page<T> & EN docs deferred |

---

## Documentation Standards

### Two Types of Docs

1. **Learning Docs** (`docs/html/`) — HTML files for Hien to learn:
   - Location: `docs/html/vi/phase{N}/` and `docs/html/en/phase{N}/`
   - Format: HTML with shared `styles.css`
   - Content: Explain code concepts, patterns, decisions — teaching material
   - Local-only, ignored by Git

2. **Session Notes** (`docs/session-notes/`) — Markdown handoff notes:
   - Format: `.md` files (`session-001.md`, `session-002.md`, ...)
   - Content: What done, current state, next steps — session handoff
   - Committed to Git

### Context-Low Handoff

When context is around 75% used, Hien mentions context is nearly full, or the next agent may lose important state:
1. Stop new feature/fix work and preserve context first.
2. Create the next `docs/session-notes/session-XXX.md` or update the latest note if it is the same handoff.
3. Record current branch, PRs, tests run/not run, changed files, blockers, phase status, and priority order.
4. Add a copy-paste prompt for Hien to start a new session and ask the next agent to inspect quickly.
5. Ensure PR review/coder prompts live in `docs/` follow-up files, not only chat.

### Feature Documentation

Every significant feature needs:
1. Domain model documentation (in `docs/`)
2. ADR for any non-obvious decision
3. Sequence diagram for complex flows (payment, booking conflict)
4. State machine diagram for entities with lifecycle (Booking, Order, Payment)

## Learning Model (IMPORTANT)

This project is **vibe-coded**: Claude implements, Hien learns by reading docs + studying code.
- Hien's goal: **understand every single line** of code in this project.
- When implementing, add **inline comments explaining WHY** — not just what the code does.
- Every Java concept used (generics, annotations, beans, etc.) should have a brief comment or doc reference.
- The `docs/learning-roadmap.md` maps concepts to code — keep it updated as implementation progresses.
- If Hien asks "explain this code", explain thoroughly with references to Java/Spring concepts.
- Project must cover all fields Hien mentioned: OOP, DB design, system design, payment — and be a usable application.

## Agent Setup

- **No agent teams** — sequential project, small codebase, single agent is more coherent.
- Main agent = implementer + explainer.
- Use subagents only for read-only tasks (verify, search, explore).
- `verifier` agent: runs builds/tests, reports results, never edits code.

## Testing

- Service layer: 80%+ coverage target
- Integration tests for: payment webhook, booking concurrency, API endpoints
- Use TestContainers for PostgreSQL in tests

## Git & GitHub (MANDATORY)

### Commit Identity
- **NEVER** add `Co-Authored-By: Claude` or any AI co-author to commit messages
- Commits must show as authored by Hien (`FrorsttzNguyen / fangrixian.nguyenn@outlook.com`)
- Commit messages: English, imperative mood, concise

### Conventional Commits
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `refactor:` code restructuring without behavior change
- `test:` adding or updating tests
- `chore:` build, dependencies, config

### Git Flow
- `main` — stable, only merge via PR
- `feat/phaseN-description` — feature branches
- `fix/description` — bug fix branches
- One feature per branch, PR with description
- Squash merge or regular merge (no fast-forward on main)
- See `docs/git-flow.md` for full details

### GitHub CLI (`gh`)
- Use `gh` CLI for all GitHub operations (create repo, PRs, issues)
- Create repo: `gh repo create <name> --public --source=. --push`
- Create PR: `gh pr create --title "feat: ..." --body "description"`
- List PRs: `gh pr list`
- Merge PR: `gh pr merge <number> --squash`

### PR Review Handoff Rule

When reviewing any PR or branch for Hien:
1. **Review the actual diff**, not only the coder's summary.
2. **Run tests or state clearly why tests were not run.** Passing tests do not mean the PR is safe.
3. **Write or update one review follow-up file under `docs/` before the session ends.** Do not leave fix instructions only in chat because the next coder/reviewer session may miss that context. The file must include:
   - PR/branch name and URL
   - exact findings with file paths/line references
   - severity and why each issue matters
   - a short verdict Hien can send verbatim to the coder agent, e.g. "Amend PR #N; do not merge yet"
   - copy-paste prompt for the coder agent to amend the PR
   - test commands and expected verification
4. **Update the latest session note** with the review result and link to the follow-up file.
5. If the PR should not merge, say **"do not merge yet"** explicitly and list the blockers.
6. When the coder amends the same PR, start the next review from that `docs/` follow-up file and update it instead of scattering new prompts only in chat.

### What's AI-assisted vs Developer Code
- Files in `.agentsignore` are AI working files (AGENTS.md, .agents/)
- `docs/.ai-generated-note.md` tracks initially AI-generated files
- Developer should refactor AI-scaffolded code as understanding grows
- Final portfolio = developer's own work, not AI output
