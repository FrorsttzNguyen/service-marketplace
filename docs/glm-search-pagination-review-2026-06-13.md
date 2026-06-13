# GLM Service Search Pagination/Sorting Review — 2026-06-13

## Scope

Review of GLM's reported changes for service search pagination and sorting behavior on current working tree.

Branch observed: `main`

Important repository state: working tree still has unresolved merge/index conflicts (`AA`/`UU`) in several files, so this review is based on the visible working-tree file contents, not a clean merge state.

## Verdict

**Do not treat this as fully correct yet.** The implementation improves pagination validation and invalid-sort handling, but it misses one prompt requirement: deterministic sorting when multiple services share the primary sort value.

## Findings

### High — Deterministic sort requirement is not actually implemented

Files:
- `src/main/java/com/hien/marketplace/interfaces/rest/ServiceController.java:87-90`
- `src/main/java/com/hien/marketplace/application/service/ServiceCatalogService.java:55-57`

Issue:
`@PageableDefault(sort = "createdAt")` only applies when the client does not provide a `sort` parameter. When the client sends `sort=basePrice.amountCents,asc`, Spring does not automatically append `createdAt` or `id` as a secondary tie-breaker.

Why it matters:
Rows with the same `basePrice.amountCents` can be returned in database-dependent order. That makes pagination unstable and can cause duplicate/missing rows across pages when records tie on the primary sort.

Expected fix:
Append a stable tie-breaker, preferably `id`, when the pageable sort does not already include it. Example behavior:

```text
sort=basePrice.amountCents,asc
=> basePrice.amountCents ASC, id ASC
```

### High — Deterministic sort test is a false positive

File:
- `src/test/java/com/hien/marketplace/integration/ServiceSearchIntegrationTest.java:371-378`

Issue:
The test creates same-price services, calls `sort=basePrice.amountCents,asc`, but only asserts that `$.content` is an array. It does not assert stable order.

Why it matters:
The test passes even if the API is nondeterministic. It proves almost nothing about the requirement.

Expected fix:
Assert exact ordering for tied rows, ideally using IDs or predictable titles after a secondary sort is deliberately implemented.

### Medium — Global `IllegalArgumentException` handler is broad and message-fragile

File:
- `src/main/java/com/hien/marketplace/interfaces/rest/GlobalExceptionHandler.java:282-307`

Issue:
The handler catches every `IllegalArgumentException`, then checks whether the message contains `"Page index"` or `"Page size"`.

Why it matters:
This repeats the same pattern previously flagged for broad `IllegalStateException`: unrelated programmer errors can be intercepted by a client-error handler path, and the classification depends on exception message text.

Expected fix:
Use a dedicated exception type from the pagination resolver, e.g. `InvalidPaginationParameterException`, and handle that specific type with 400.

### Medium — Custom pageable resolver is acceptable but heavier than ideal

Files:
- `src/main/java/com/hien/marketplace/interfaces/rest/ValidatingPageableResolver.java`
- `src/main/java/com/hien/marketplace/config/WebMvcConfig.java`

Issue:
A global custom `PageableHandlerMethodArgumentResolver` can work, but it is a heavier abstraction and should be verified carefully because Spring Boot/Spring Data already configures pageable resolution.

Why it matters:
If resolver ordering changes or another resolver handles `Pageable` first, validation may be bypassed. Current integration tests appear to exercise it, but the approach should remain minimal and specific.

## Positive notes

- Negative page validation is implemented in `ValidatingPageableResolver`.
- Zero/negative size validation is implemented.
- Excessive size is rejected consistently at `> 100`.
- Invalid sort fields are mapped to 400 instead of 500 via `PropertyReferenceException` handling.
- Tests were reportedly run by GLM, but this local working tree is not clean, so verify again after resolving conflicts.

## Verdict Hien can send to coder agent

Amend the service search pagination/sorting changes; do not consider the task complete yet. Keep the pagination validation and invalid-sort 400 behavior, but implement real deterministic tie-break sorting for client-provided sorts and replace the false-positive deterministic sort test with assertions that prove exact stable order. Prefer a dedicated pagination exception type instead of globally catching `IllegalArgumentException` by message text. Re-run `./mvnw test -Dtest=ServiceSearchIntegrationTest` and then `./mvnw test` after conflicts are resolved.

## Suggested verification commands

```bash
./mvnw test -Dtest=ServiceSearchIntegrationTest
./mvnw test
```

Expected:
- Service search tests pass.
- Full suite passes from a clean, non-conflicted working tree.
