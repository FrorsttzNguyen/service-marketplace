# Session Handoff Note — Session 003

**Date:** 2026-06-11
**Phase:** Phase 1 domain alignment complete → Local bilingual HTML docs planned
**Status:** Code/tests verified; next session should focus on full VI/EN HTML learning docs only

## Hien's Latest Request

Hien asked to rewrite the plan and note it fully in a session note. Hien's intended next step is to start a new session and ask for implementation later.

Key instruction from Hien:

- Do not commit documentation changes.
- Current docs are Vietnamese-first; add a full English version.
- Docs should have complete Vietnamese and English coverage.
- Next session should implement from the plan, not improvise.

The updated plan is saved at:

```text
/Users/hiennguyen/.claude/plans/zippy-scribbling-cray.md
```

## Important Project Rules to Preserve

- Address Hien as "Hien" in every reply.
- This project is a learning vehicle; docs should teach the code line-by-line and concept-by-concept.
- No agent teams for normal implementation; sequential work is preferred.
- Do not add `Co-Authored-By: Claude` to commits.
- Do not commit credentials.
- Do not commit docs unless Hien explicitly changes the instruction.
- `docs/html/` is local-only and ignored through `.git/info/exclude`.

## Current Technical State Before Next Session

Phase 1 domain-code alignment was already implemented and verified before this note.

Important completed code changes:

- `Booking.changeStatus()` validates transitions from the booking's actual current status.
- `BookingStatusHistory.fromStatus` and `toStatus` persist with `EnumType.STRING`.
- `Vendor.user` uses `@OneToOne`, matching the unique index on `vendors.user_id`.
- `PhoneNumber` is a JPA embeddable value object and is embedded in `User`.
- `TimeSlot` is embedded in `Booking` and `ServiceAvailability`.
- `Money` is embedded in money-related entities such as `ServiceEntity`, `Booking`, `Order`, `Payment`, and `Refund`.
- `BookingRepository` derived query was updated for embedded `TimeSlot`:
  - `existsByServiceIdAndBookingDateAndTimeSlotStartTime(...)`
- `SecurityConfig` comments were corrected to say JWT is planned for Phase 2, not currently implemented.

Verification already run:

```text
./mvnw clean test
Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Documentation State Before Next Session

The Vietnamese HTML docs were previously corrected for technical accuracy:

- Booking concurrency docs now distinguish:
  - create-time double booking: database unique constraint
  - update-time conflict: optimistic locking via `@Version`
- Stripe docs now distinguish:
  - `payments.stripe_payment_intent_id` prevents duplicate local payment rows for one PaymentIntent
  - `stripe_event_log.stripe_event_id` is for future webhook event idempotency
- Architecture docs now describe the project as pragmatic Spring layered architecture, not pure framework-free Clean Architecture.
- Phase 1 docs now mention 14 JPA entities and 60 domain tests + 1 Spring context test.
- Value object docs now match the code: `Money`, `TimeSlot`, and `PhoneNumber` are used in real entities.

A scaffold bilingual structure may already exist locally under `docs/html/vi/...` and `docs/html/en/...`, but the next session must treat the English pages as needing a quality pass to ensure they are full equivalents, not shortened summaries.

## Final Plan for Next Session

### Goal

Build full bilingual HTML learning docs under `docs/html/`, with Vietnamese and English versions covering Phase 0 and Phase 1 completely.

The result should let Hien open:

```text
docs/html/index.html
```

and choose:

- Vietnamese Phase 0
- Vietnamese Phase 1
- English Phase 0
- English Phase 1

### Hard constraints

1. Do not commit docs.
2. Do not stage docs.
3. Do not change application code for this docs-only task.
4. Keep `docs/html/` local-only and ignored.
5. Preserve old paths:
   - `docs/html/phase0/*.html`
   - `docs/html/phase1/*.html`
6. English docs must be full learning-document equivalents, not summaries.

### Target folder structure

```text
docs/html/
  index.html
  phase0/                 # existing old-path Vietnamese docs; keep for compatibility
  phase1/                 # existing old-path Vietnamese docs; keep for compatibility
  vi/
    phase0/
      01-java-spring-fundamentals.html
      02-database-design.html
      03-oop-design-patterns.html
      04-system-design.html
      05-project-architecture.html
      styles.css
    phase1/
      01-database-schema.html
      02-value-objects.html
      03-jpa-entities.html
      04-design-patterns.html
      05-repositories-and-tests.html
      styles.css
  en/
    phase0/
      01-java-spring-fundamentals.html
      02-database-design.html
      03-oop-design-patterns.html
      04-system-design.html
      05-project-architecture.html
      styles.css
    phase1/
      01-database-schema.html
      02-value-objects.html
      03-jpa-entities.html
      04-design-patterns.html
      05-repositories-and-tests.html
      styles.css
```

### Implementation steps

1. Confirm `docs/html/` is ignored:

   ```bash
   git check-ignore -v docs/html/index.html docs/html/phase0/01-java-spring-fundamentals.html
   ```

   If it is not ignored, stop and ask Hien before changing ignore rules.

2. Preserve Vietnamese source docs:

   - Treat `docs/html/phase0/*.html` and `docs/html/phase1/*.html` as the current Vietnamese source.
   - Copy them into `docs/html/vi/phase0/` and `docs/html/vi/phase1/`.
   - Do not delete old paths.

3. Create English equivalents:

   Phase 0:
   - `docs/html/en/phase0/01-java-spring-fundamentals.html`
   - `docs/html/en/phase0/02-database-design.html`
   - `docs/html/en/phase0/03-oop-design-patterns.html`
   - `docs/html/en/phase0/04-system-design.html`
   - `docs/html/en/phase0/05-project-architecture.html`

   Phase 1:
   - `docs/html/en/phase1/01-database-schema.html`
   - `docs/html/en/phase1/02-value-objects.html`
   - `docs/html/en/phase1/03-jpa-entities.html`
   - `docs/html/en/phase1/04-design-patterns.html`
   - `docs/html/en/phase1/05-repositories-and-tests.html`

4. Translation quality rule:

   English pages must preserve the same learning depth as Vietnamese pages:

   - same major sections
   - same technical claims
   - same examples where relevant
   - same diagrams/tables/callouts where present
   - same current-vs-future distinctions
   - code snippets preserved or translated only in comments where that helps learning

   Do not create short English summaries if the Vietnamese page is long.

5. Add language switcher to bilingual pages:

   ```text
   VI | EN
   ```

   Example links:

   ```text
   vi/phase1/02-value-objects.html -> ../../en/phase1/02-value-objects.html
   en/phase1/02-value-objects.html -> ../../vi/phase1/02-value-objects.html
   ```

6. Add/update landing page:

   ```text
   docs/html/index.html
   ```

   It should link to VI/EN Phase 0 and VI/EN Phase 1, and say that `docs/html/` is local-only and ignored by Git.

7. Keep CSS low-risk:

   - Copy current phase CSS to each language/phase folder.
   - Add only minimal classes for language switcher and landing page cards.
   - Do not refactor all CSS into a shared asset unless Hien asks later.

## Technical Claim Checklist for Both Languages

Every VI/EN page should preserve these truths:

- Phase 1 currently covers schema, JPA entities, value objects, Spring Data repositories, and tests.
- REST controllers, DTO validation, JWT auth, application services, Redis cache behavior, Stripe SDK/webhook controller, TestContainers, and frontend are future phases unless implemented later.
- Booking creation conflicts are protected by `UNIQUE(service_id, booking_date, start_time)`.
- `@Version` protects concurrent updates to an existing booking row.
- `Vendor.user` is `@OneToOne`, matching the unique index on `vendors.user_id`.
- `Money`, `TimeSlot`, and `PhoneNumber` are embedded value objects in the current Phase 1 model.
- `Booking.changeStatus()` validates from the actual current status.
- `stripe_event_log.stripe_event_id` is for future webhook event idempotency.
- `payments.stripe_payment_intent_id` prevents duplicate local payment rows for the same PaymentIntent.
- Current verified tests: 61 total tests, 60 domain tests + 1 Spring context test.
- Current entity count in Phase 1 docs: 14 JPA entity classes.

## Verification for Next Session

Run static checks after implementation.

### HTML link/resource check

Check all HTML files under `docs/html/**/*.html`:

- every local `.html` link resolves
- every CSS link resolves
- same-page anchors resolve
- cross-page anchors resolve if used
- links do not escape `docs/html/` unexpectedly

### HTML entity check

Ensure no invalid raw ampersands remain:

```text
& must be &amp; unless it is a valid entity such as &rarr; or &mdash;
```

### Bilingual completeness check

Verify:

- every `docs/html/vi/phase*/NN-*.html` has a matching `docs/html/en/phase*/NN-*.html`
- every `docs/html/en/phase*/NN-*.html` has a matching `docs/html/vi/phase*/NN-*.html`
- Vietnamese pages use `<html lang="vi">`
- English pages use `<html lang="en">`
- every bilingual page has a language switcher

### Stale-claim grep

Search for stale/wrong claims, including:

```text
35 tests
13 classes
BookingStatus.PENDING.throwIfInvalidTransition(BookingStatus.CONFIRMED)
Project này dùng JWT
JWT is implemented
Redis cache behavior is implemented
Stripe SDK is implemented
customer bị charge 2 lần
customer is charged twice
KHÔNG phụ thuộc framework
framework-free domain
```

Expected: no stale claims in docs.

### Git safety check

Run:

```bash
git status --short --ignored docs/html
git status --short
```

Expected:

- `docs/html/` appears as ignored: `!! docs/html/`
- no docs are staged
- if a later code commit is needed, stage only code/test files intentionally, not docs

## Completion Criteria

The next session's docs task is complete only when:

1. `docs/html/index.html` exists and links to VI/EN Phase 0/Phase 1.
2. Vietnamese docs exist under `docs/html/vi/...`.
3. English docs exist under `docs/html/en/...`.
4. English docs are full learning-document equivalents, not summaries.
5. Language switcher works on every bilingual page.
6. Static verification passes.
7. `docs/html/` remains ignored and uncommitted.

## Recommended Prompt for Next Session

Use this exact prompt:

```text
Implement the plan at /Users/hiennguyen/.claude/plans/zippy-scribbling-cray.md.
Important: English docs must be full equivalent learning documents, not summaries.
Do not commit docs. Keep docs/html local-only and ignored.
Do not change application code for this docs-only task.
Also read docs/session-notes/session-003.md before starting.
```

## Current Git Reminder

At the time of this note, the working tree had tracked modifications from earlier code/docs alignment work. If Hien asks for commits later, stage carefully.

Known important rule from Hien:

- Do not commit docs.

Because `docs/html/` is ignored, it should not appear in normal `git status`, but tracked Markdown docs can still appear if edited.
