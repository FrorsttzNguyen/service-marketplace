# Session 023: Vendor Booking Endpoints and Tests

**Date:** 2026-06-13
**Branch:** fix/phase4-audit-correctness

## Summary

Added missing vendor booking management endpoints to BookingController and comprehensive integration tests. Fixed GlobalExceptionHandler to properly handle domain-layer IllegalStateException for invalid state transitions.

## What Was Done

### 1. Added Vendor Endpoints to BookingController

Added 3 new REST endpoints for vendor booking management:

- `PUT /api/bookings/{id}/confirm` - Vendor confirms a pending booking (PENDING → CONFIRMED)
- `PUT /api/bookings/{id}/start` - Vendor starts service (CONFIRMED → IN_PROGRESS)
- `PUT /api/bookings/{id}/complete` - Vendor completes service (IN_PROGRESS → COMPLETED)

Each endpoint:
- Requires VENDOR role (enforced at URL level in SecurityConfig)
- Validates ownership (only vendor who owns the service can manage the booking)
- Returns updated BookingResponse with new status

### 2. Updated SecurityConfig

Added URL-based role guards for new endpoints:

```java
.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/confirm").hasRole("VENDOR")
.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/start").hasRole("VENDOR")
.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/bookings/*/complete").hasRole("VENDOR")
```

### 3. Fixed GlobalExceptionHandler

Added handler for `IllegalStateException` to map domain-layer state transition errors to 422:

```java
@ExceptionHandler(IllegalStateException.class)
public ResponseEntity<ErrorResponse> handleIllegalState(
        IllegalStateException ex,
        HttpServletRequest request
) {
    ErrorResponse response = ErrorResponse.of(
            "BUSINESS_RULE_VIOLATION",
            ex.getMessage()
    );
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
}
```

**Why:** Domain entities throw `IllegalStateException` from `BookingStatus.throwIfInvalidTransition()` for invalid transitions. Previously returned 500, now correctly returns 422.

### 4. Added Integration Tests

Added `VendorBookingManagementTests` nested class with 18 tests covering:

**Success scenarios:**
- Confirm booking successfully
- Start service successfully
- Complete service successfully
- Full booking lifecycle (PENDING → CONFIRMED → IN_PROGRESS → COMPLETED)

**Authorization tests:**
- Reject confirmation by non-owner vendor
- Reject confirmation by customer (403 Forbidden)
- Reject start service by non-owner vendor
- Reject start service by customer
- Reject complete service by non-owner vendor
- Reject complete service by customer
- Reject all actions without authentication

**State machine validation:**
- Reject confirmation of cancelled booking
- Reject start service on pending booking (must confirm first)
- Reject complete service on confirmed booking (must start first)
- Reject complete service on pending booking

**Resource not found:**
- Reject confirmation of non-existent booking

## Files Changed

1. `src/main/java/com/hien/marketplace/interfaces/rest/BookingController.java` - Added 3 vendor endpoints
2. `src/main/java/com/hien/marketplace/config/SecurityConfig.java` - Added VENDOR role guards
3. `src/main/java/com/hien/marketplace/interfaces/rest/GlobalExceptionHandler.java` - Added IllegalStateException handler
4. `src/test/java/com/hien/marketplace/integration/BookingControllerIntegrationTest.java` - Added 18 new tests

## Test Results

```
Tests run: 311, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All tests passing including:
- 32 BookingController integration tests (18 new)
- All other existing tests

## Current State

- Branch: fix/phase4-audit-correctness
- All tests passing (311/311)
- No pending PRs on this branch
- Changes ready for commit

## Next Steps

1. Commit changes with message:
   ```
   feat: Add vendor booking management endpoints

   - Add confirm, start, complete endpoints to BookingController
   - Add VENDOR role guards in SecurityConfig
   - Add IllegalStateException handler for state transition errors
   - Add 18 integration tests for vendor booking management
   ```

2. Push to remote and create PR if needed

3. Continue with Phase 4 audit fixes (see tasks/todo.md)

## Learning Points

**Why IllegalStateException to 422?**

Domain layer uses `IllegalStateException` for state machine violations because:
- It's a standard Java exception (no custom exception needed)
- Clear semantic: "this operation is illegal in current state"
- GlobalExceptionHandler centralizes the mapping to HTTP 422

**State Machine Pattern in Booking:**

```
PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
    ↓          ↓
CANCELLED  CANCELLED
```

Each transition is validated in `BookingStatus.throwIfInvalidTransition()`, ensuring business rules are enforced at the domain level, not scattered across controllers/services.
