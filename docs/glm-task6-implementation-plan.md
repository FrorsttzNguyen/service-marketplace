# Task 6: Wire ServiceSpecification into Real API — 1-Shot Implementation Plan

**Date:** 2026-06-13
**Target model:** GLM-5 (1-shot, no follow-up)
**Branch:** `fix/phase4-audit-correctness` (or create `feat/service-search-api`)

---

## Problem

`ServiceSpecification` and `ServiceSearchRequest` exist but are **never called** from any controller or service. The search/filter feature is dead code. We need to wire it into a real API endpoint.

## Existing Code (DO NOT MODIFY these files — they are already correct)

| File | Status |
|------|--------|
| `infrastructure/persistence/specification/ServiceSpecification.java` | Complete — `fromRequest()` builds dynamic Specification |
| `interfaces/dto/request/ServiceSearchRequest.java` | Complete — record with validation annotations |
| `infrastructure/persistence/ServiceRepository.java` | Complete — extends `JpaSpecificationExecutor<ServiceEntity>` |

## Files to Modify (2 files)

### 1. `ServiceController.java`

**Location:** `src/main/java/com/hien/marketplace/interfaces/rest/ServiceController.java`

**What to add:** One new endpoint method `searchServices`.

**Exact requirements:**
- Add `GET /api/services/search` endpoint
- Method signature: `searchServices(ServiceSearchRequest request, Pageable pageable)`
- Use `@Valid` on request for validation annotations
- Use `@PageableDefault(size = 20, sort = "createdAt")` for pageable
- Spring automatically binds query params to record fields (no `@RequestParam` needed per field)
- Delegate to `serviceCatalogService.searchServices(request, pageable)`
- Return `ResponseEntity<Page<ServiceResponse>>`
- Add `@Operation` Swagger annotation matching existing style
- This is a PUBLIC endpoint (no auth required) — already covered by `/api/services/**` permitAll in SecurityConfig

**Required imports to add:**
- `com.hien.marketplace.interfaces.dto.request.ServiceSearchRequest`
- `jakarta.validation.Valid`

**IMPORTANT:** Place this method BEFORE `getServiceById(@PathVariable Long id)`. Spring MVC resolves `/api/services/search` and `/api/services/{id}` by order — if `{id}` comes first, "search" gets parsed as an ID. The safest approach: put `searchServices` before `getServiceById` in the class.

### 2. `ServiceCatalogService.java`

**Location:** `src/main/java/com/hien/marketplace/application/service/ServiceCatalogService.java`

**What to add:** One new method `searchServices`.

**Exact requirements:**
- Method: `public Page<ServiceResponse> searchServices(ServiceSearchRequest request, Pageable pageable)`
- Annotate with `@Transactional(readOnly = true)`
- Body: `serviceRepository.findAll(ServiceSpecification.fromRequest(request), pageable).map(this::enrichServiceResponse)`
- `enrichServiceResponse` already exists in this class — reuse it

**Required imports to add:**
- `com.hien.marketplace.infrastructure.persistence.specification.ServiceSpecification`
- `com.hien.marketplace.interfaces.dto.request.ServiceSearchRequest`

## File to Create (1 file)

### 3. `ServiceSearchIntegrationTest.java`

**Location:** `src/test/java/com/hien/marketplace/integration/ServiceSearchIntegrationTest.java`

**Setup:** Follow the same pattern as `BookingControllerIntegrationTest.java`:
- `@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles("test")`, `@Transactional`
- `@BeforeEach` creates test data: vendor user → vendor → category → multiple services with different attributes

**Test data to create in `@BeforeEach`:**
- 1 vendor user (registered as vendor, approved)
- 1 category (e.g., "Haircut")
- 3 services with varying attributes:
  - Service A: name="Premium Haircut", price=10000 cents ($100), city="hanoi", status=ACTIVE
  - Service B: name="Basic Massage", price=5000 cents ($50), city="saigon", status=ACTIVE
  - Service C: name="VIP Haircut", price=20000 cents ($200), city="hanoi", status=INACTIVE (deactivated)

**Test cases:**

| # | Test name | Request | Expected |
|---|-----------|---------|----------|
| 1 | `searchWithNoFilters_returnsActiveOnly` | `GET /api/services/search` (no params) | 2 results (A, B), NOT C (inactive) |
| 2 | `searchByKeyword_matchesNameCaseInsensitive` | `?keyword=haircut` | 1 result (A only), NOT C (inactive) |
| 3 | `searchByCategory_filtersCorrectly` | `?categoryId={id}` | Returns services in that category |
| 4 | `searchByCity_exactMatch` | `?city=hanoi` | 1 result (A), NOT C (inactive) |
| 5 | `searchByPriceRange_filtersCents` | `?minPrice=60&maxPrice=150` | 1 result (A, price=$100) |
| 6 | `searchByKeywordNoMatch_returnsEmpty` | `?keyword=nonexistent` | 0 results |
| 7 | `searchCombinedFilters` | `?keyword=haircut&city=hanoi` | 1 result (A) |

**Test style:**
- Use `mockMvc.perform(get("/api/services/search").param("keyword", "haircut"))` — no auth header needed (public endpoint)
- Assert with `.andExpect(status().isOk())` and `jsonPath("$.content")` for paginated results
- `.andExpect(jsonPath("$.content", hasSize(N)))` — import `org.hamcrest.Matchers.hasSize`

**IMPORTANT test details:**
- Service entity field is `name` (not `title`). ServiceSpecification searches `name` and `description`. ServiceResponse maps `name` → `title` via ServiceMapper. Tests should search by the value in `name` field.
- Price params are in dollars (BigDecimal), but Money stores cents. `minPrice=60&maxPrice=150` means 6000-15000 cents. ServiceSpecification already handles this conversion.
- No auth needed — `/api/services/**` is permitAll in SecurityConfig.
- The Category entity needs to be saved before creating services. Use `categoryRepository.save(new Category("Haircut"))`.
- Services must be activated: call `service.activate()` before save.

## What NOT to do

- Do NOT modify `ServiceSpecification.java` — it's already correct
- Do NOT modify `ServiceSearchRequest.java` — it's already correct  
- Do NOT modify `ServiceRepository.java` — it already extends `JpaSpecificationExecutor`
- Do NOT modify `SecurityConfig.java` — `/api/services/**` is already public
- Do NOT add sorting logic beyond what Pageable provides (ignore `sortBy`/`sortOrder` fields in ServiceSearchRequest for now)
- Do NOT add `@EntityGraph` to the Specification-based `findAll` — it uses a different code path than named queries

## Verification

After implementation, run:
```bash
./mvnw test -q 2>&1 | grep -E 'Tests run|BUILD'
```

Expected: all existing tests still pass (286+) plus new search tests pass.

---

## GLM Prompt (copy-paste this)

```text
Read the implementation plan at docs/glm-task6-implementation-plan.md

Then implement Task 6 exactly as described. You need to:

1. Modify ServiceController.java — add GET /api/services/search endpoint (place it BEFORE getServiceById to avoid path conflict with {id})
2. Modify ServiceCatalogService.java — add searchServices method using ServiceSpecification.fromRequest()
3. Create ServiceSearchIntegrationTest.java — integration tests for the search endpoint

Before coding, read these existing files to understand the patterns:
- ServiceSpecification.java (the spec builder you're wiring in)
- ServiceSearchRequest.java (the DTO with query params)
- ServiceRepository.java (already extends JpaSpecificationExecutor)
- ServiceController.java (add your endpoint here)
- ServiceCatalogService.java (add your method here)
- BookingControllerIntegrationTest.java (follow this test pattern)

After implementing, run ./mvnw test and report results.

Do NOT modify ServiceSpecification, ServiceSearchRequest, ServiceRepository, or SecurityConfig.
```
