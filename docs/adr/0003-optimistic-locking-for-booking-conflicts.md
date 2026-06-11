# ADR 0003: Booking Concurrency Protection

## Status
Accepted

## Context
When two customers try to book the same service at the same time slot simultaneously, we need to prevent double-booking.

There are two different concurrency problems:

1. **Creating a booking for an empty slot** — there may be no existing booking row yet, so `@Version` has nothing to lock or compare.
2. **Updating an existing booking row** — multiple actors may try to confirm/cancel/start/complete the same booking concurrently.

Options considered:

1. **Database unique constraint** — Prevent duplicate booking rows at DB level.
2. **Optimistic locking** (`@Version` in JPA) — Detect stale updates to an existing row.
3. **Pessimistic locking** (`SELECT ... FOR UPDATE`) — Lock a row, second request waits.
4. **Redis distributed lock** — Lock before checking availability.

Expected concurrent bookings per slot: very low (1-2 conflicts at most for a portfolio project).

## Decision
Use a **database unique constraint as the authoritative protection for booking creation**, and use **optimistic locking for concurrent updates to an existing booking**.

```sql
ALTER TABLE bookings
ADD CONSTRAINT uq_booking_slot UNIQUE (service_id, booking_date, start_time);
```

```java
@Entity
public class Booking {
    @Id @GeneratedValue
    private Long id;

    @Version
    private Long version;
}
```

Application-level availability checks are still useful for UX, but they are not the final guarantee. Two requests may both see a slot as available; only one insert can commit because of the unique constraint. The losing request should become `409 Conflict` once the API layer exists.

## Consequences

### Positive
- **Correct create-time guarantee** — The database prevents duplicate rows for the same `(service_id, booking_date, start_time)`.
- **Non-blocking updates** — `@Version` avoids holding locks while still catching stale updates to an existing booking.
- **Simple to explain** — Unique constraint for insert conflicts, optimistic locking for update conflicts.
- **Database constraint as source of truth** — Even if application availability logic has a race, the database protects integrity.

### Negative
- **Application must translate constraint violations** — Duplicate booking insert should become a user-facing `409 Conflict`.
- **Optimistic locking is not enough for create conflicts** — It only applies after a row exists.
- **Not suitable for extreme contention** — If 100 users book the same slot simultaneously, many inserts fail. For this project and most local-service bookings, that is acceptable.

### Handling Strategy
```java
public Booking createBooking(CreateBookingRequest request) {
    // 1. Check availability for UX.
    // 2. Try INSERT booking.
    // 3. If uq_booking_slot is violated, return 409 Conflict.
}
```

For updates to an existing booking, JPA uses `@Version`:

```java
// UPDATE bookings SET status=?, version=version+1
// WHERE id=? AND version=?
```

If another transaction already updated the same booking, JPA throws an optimistic locking exception and the API should return a conflict or retry depending on the use case.
