# Review Plan — Full Project Audit

## Scope

Review the Service Marketplace repository as a learning/portfolio project:
- Phase 0–4 implementation quality
- Markdown documentation and ADRs
- HTML learning docs
- Tests/build health
- Alignment with project goals: Java/Spring learning vehicle, modular monolith, layered architecture, domain-rich design, PostgreSQL/Redis/Stripe, portfolio readiness

## Tasks

- [x] Map repository structure, docs, phase boundaries, and evidence sources.
- [x] Review Phase 0: foundation, architecture, learning docs, tests.
- [x] Review Phase 1: domain model/value objects/JPA schema/repositories/tests/docs.
- [x] Review Phase 2: API/security/validation/docs/tests.
- [x] Review Phase 3: booking/order business logic/use cases/docs/tests.
- [x] Review Phase 4: payment/refund/webhook implementation/docs/tests.
- [x] Run verification evidence for build/tests.
- [x] Synthesize an honest scorecard, gaps, risks, and next-step documentation/coding plan.

## Verification

- Used read-only phase agents to keep context isolated.
- Cross-checked claims against actual files, docs, and test output.
- Ran `./mvnw test`: 284 tests passed, 0 failures/errors/skips.
- Ran HTML internal link check: 67 HTML files, 600 internal links checked, 27 broken internal links.

## Review Notes

### High-confidence findings

1. Phase 0–4 are broadly implemented and the project is strong as a learning backend portfolio foundation.
2. The test suite currently passes, but it mostly uses H2 with Flyway disabled rather than PostgreSQL/TestContainers.
3. HTML learning docs exist for VI and EN across phases 0–4, but landing/index and language-switch links are stale/broken for Phase 3/4.
4. Critical code review gaps remain around payment/webhook transactional behavior, double-booking database protection, role authorization, and unused ServiceSpecification search.

### Recommended next work

1. Fix correctness/security gaps before increasing phase scores.
2. Repair HTML docs navigation/index/language switcher and update stale evaluation claims.
3. Add PostgreSQL/TestContainers tests for migrations, jsonb mapping, booking overlap constraints, ServiceSpecification, and payment webhook idempotency.
4. Then continue with documentation cleanup or Phase 5 Redis caching.
