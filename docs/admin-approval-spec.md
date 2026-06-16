# Spec — Backend Completion: Admin Vendor Approval

**Type:** Small backend feature (pre-Phase 6)
**For:** GLM to implement → Claude to review PR
**Branch:** `feat/admin-vendor-approval` off `main` (after PR #7 merges)
**Estimated size:** ~1–2 days, small surface

---

## Why this exists (the bug being fixed)

Vendor onboarding is a **dead-ended flow** today:

- `Vendor` is constructed with `verificationStatus = PENDING` (`domain/vendor/Vendor.java:69`).
- `VendorServiceManagement.createService` throws `BusinessRuleViolationException` when
  `!vendor.isApproved()` (`application/service/VendorServiceManagement.java:116`).
- `Vendor.approve()` / `Vendor.reject()` exist (`domain/vendor/Vendor.java:87-92`) **but no code calls
  them** — confirmed by grep across `src/main`.
- `SecurityConfig` reserves `/api/admin/**` for `hasRole("ADMIN")` but **there is no AdminController**.

Net: a vendor who registers can never be approved through the API, so they can never create a service.
This spec adds the admin path that approves/rejects vendors.

**Out of scope:** review moderation (the `Review` domain has no hide/remove method — that is a larger,
separate change). Do NOT add it here.

---

## Scope — what to build

### 1. Application service — `AdminVendorService` (`application/service/`)
- `Page<VendorAdminResponse> listVendors(VerificationStatus status, Pageable pageable)`
  - If `status` is null → return all; else filter by status.
- `VendorAdminResponse approveVendor(Long vendorId)` — load vendor or throw
  `ResourceNotFoundException("Vendor", vendorId)`; call `vendor.approve()`; save; return response.
  - Idempotency: approving an already-APPROVED vendor is a no-op success (don't throw). Rejecting an
    already-REJECTED vendor likewise. (Decide via the domain state; keep it simple.)
- `VendorAdminResponse rejectVendor(Long vendorId)` — symmetric, calls `vendor.reject()`.
- Methods are `@Transactional` (writes) / `@Transactional(readOnly = true)` (list), matching the
  existing service conventions in this codebase.

### 2. REST controller — `AdminController` (`interfaces/rest/`)
Base path `/api/admin`. Follow the existing controller style (`VendorServiceController` is a good
template: constructor injection, `ResponseEntity`, OpenAPI annotations, extracts auth principal).

| Method | Path | Body | Returns |
|--------|------|------|---------|
| GET | `/api/admin/vendors` | query: `status` (optional), `page`, `size` | `200` Page of `VendorAdminResponse` |
| POST | `/api/admin/vendors/{vendorId}/approve` | — | `200` `VendorAdminResponse` |
| POST | `/api/admin/vendors/{vendorId}/reject` | — | `200` `VendorAdminResponse` |

- No new SecurityConfig rule needed — `/api/admin/**` is already `hasRole("ADMIN")`. Verify it still
  sits BEFORE the `/api/**` authenticated catch-all (it does today).

### 3. DTO — `VendorAdminResponse` (`interfaces/dto/response/`)
A record exposing: `vendorId`, `userId`, `businessName`, `verificationStatus`, `email` (or whatever
identifies the vendor's user), `createdAt`. Keep it a plain record; reuse `ServiceMapper`/a small mapper
or build inline — match how other responses are built.

### 4. Repository
- Add `Page<Vendor> findByVerificationStatus(VerificationStatus status, Pageable pageable)` to
  `VendorRepository` if not present.

### 5. Admin bootstrap (one ADMIN user must exist)
**Preferred (12-factor, teachable):** a `CommandLineRunner`/`ApplicationRunner` bean that creates an
admin user from env vars `ADMIN_EMAIL` / `ADMIN_PASSWORD` **only if** those are set and the user does
not already exist. Password hashed with the existing `PasswordEncoder`. This keeps credentials out of
source control.
- Document `ADMIN_EMAIL` / `ADMIN_PASSWORD` in `.env.example`.
- For the test profile, seed an admin directly in the integration-test setup (not via prod migration).

**Do NOT** hardcode an admin password in a Flyway migration that runs in prod — that is a credential leak
and will be flagged in review.

---

## Tests (required — this is the verification)

Integration tests (`BaseIntegrationTest` + Testcontainers + MockMvc), following
`AuthControllerIntegrationTest` / `ServiceControllerIntegrationTest` style:

1. **403 for non-admin:** a CUSTOMER/VENDOR JWT calling any `/api/admin/**` → `403`.
2. **401 for anonymous:** no token → `401`.
3. **Approve happy path:** admin approves a PENDING vendor → `200`, status becomes `APPROVED`.
4. **End-to-end unblock:** register vendor (PENDING) → admin approves → that vendor can now
   `POST /api/services` (or the vendor create endpoint) successfully. This proves the dead-end is fixed.
5. **Reject:** admin rejects → status `REJECTED`; vendor still cannot create a service.
6. **Not found:** approve a non-existent vendorId → `404`.
7. **List + filter:** `GET /api/admin/vendors?status=PENDING` returns only pending vendors, paginated.

---

## Acceptance criteria (Claude will check these on PR review)

- [ ] `env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD ./mvnw test` is green; new tests included in the count.
- [ ] No admin password hardcoded in any committed file (migration, yml, or code). Bootstrap reads env.
- [ ] `Vendor.approve()` / `reject()` domain methods are reused — no duplicated status logic in the service.
- [ ] Controller has no business logic (thin) — logic lives in `AdminVendorService` / domain, per CLAUDE.md domain-rich rule.
- [ ] OpenAPI annotations present so the endpoints show in Swagger.
- [ ] Conventional commit, English, no `Co-Authored-By` (per project Git rules).
- [ ] Inline comments explain WHY (per the project's vibe-coded learning model).

## Review handoff
When GLM opens the PR, Claude reviews per the CLAUDE.md "PR Review Handoff Rule": review the diff, run
tests, and write findings into `docs/pr-<n>-review.md`. Start from this spec's acceptance criteria.
