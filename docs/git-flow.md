# Git Flow — Service Marketplace

## Branch Strategy

```
main (protected, stable)
 ├── feat/phase0-foundation
 ├── feat/phase1-domain-model
 ├── feat/phase2-api-security
 ├── feat/phase3-business-logic
 ├── feat/phase4-payment
 ├── feat/phase5-caching
 ├── feat/phase6-frontend
 ├── feat/phase7-documentation
 └── fix/description (bug fixes)
```

## Rules

1. **`main` is sacred** — Never commit directly to main. All changes via PR.
2. **One branch per phase/feature** — Keep branches focused.
3. **Rebase before merge** — Keep history clean.
4. **Delete branch after merge** — Keep branch list clean.

## Commit Message Format

```
type(scope): concise description

[optional body with context]
```

### Types
| Type | Usage |
|------|-------|
| `feat` | New feature or significant addition |
| `fix` | Bug fix |
| `docs` | Documentation changes |
| `refactor` | Code restructuring without behavior change |
| `test` | Adding or updating tests |
| `chore` | Build, dependencies, CI, config |
| `perf` | Performance improvement |

### Examples
```
feat(booking): implement time-slot conflict detection with optimistic locking
fix(payment): handle duplicate stripe webhook delivery idempotently
docs(adr): add ADR-0003 optimistic locking for booking conflicts
test(booking): add integration test for concurrent booking scenario
refactor(domain): extract Money value object from Order entity
chore(docker): add Redis service to docker-compose
```

## PR Process

### Creating a PR
```bash
# 1. Create branch
git checkout -b feat/phase1-domain-model

# 2. Work and commit
git add .
git commit -m "feat(domain): add User, Vendor, Service entities with value objects"

# 3. Push branch
git push -u origin feat/phase1-domain-model

# 4. Create PR via gh CLI
gh pr create \
  --title "feat: Phase 1 — Domain Model + Database Design" \
  --body "## What
- Domain entities: User, Vendor, Service, Booking, Order, Payment, Review
- Value objects: Money, Address, TimeSlot
- Flyway migrations V1-V6
- Booking state machine
- Repositories for all entities

## Why
- Phase 1 of learning roadmap: OOP + Database Design fundamentals
- Establishes domain model foundation for all subsequent phases

## How to verify
- [ ] All Flyway migrations run cleanly
- [ ] Repository CRUD works for all entities
- [ ] BookingStatus rejects invalid transitions
- [ ] Money value object rejects negative amounts

## Checklist
- [x] Code compiles
- [x] Tests pass
- [x] Follows conventional commit format
- [x] No Co-Authored-By lines"
```

### Merging a PR
```bash
# Via gh CLI
gh pr merge 1 --squash

# Or manually after review
gh pr view 1          # Review the PR
gh pr merge 1 --squash # Squash merge into main
```

## GitHub CLI (`gh`) Quick Reference

```bash
# Repo management
gh repo create service-marketplace --public --source=. --push
gh repo view                        # View repo info
gh repo clone FrorsttzNguyen/service-marketplace

# PR management
gh pr create --title "feat: ..." --body "..."
gh pr list                          # List open PRs
gh pr view 1                        # View PR #1
gh pr merge 1 --squash              # Squash merge
gh pr merge 1 --merge               # Regular merge

# Issue management (optional)
gh issue create --title "..." --body "..."
gh issue list
gh issue close 1

# General
gh auth status                      # Check auth
gh api user                         # Test API access
```

## Session Handoff

When ending a work session, push all work and leave a note:

```bash
# Push current branch
git push

# Create a "handoff" commit if needed
git commit --allow-empty -m "chore: session handoff — stopping at [describe where you are]"
git push
```

Next session: check `docs/todo.md` and `docs/session-notes/` for context.
