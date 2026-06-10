# ADR 0003: Optimistic Locking for Booking Conflicts

## Status
Accepted

## Context
When two customers try to book the same service at the same time slot simultaneously, we need to prevent double-booking. Options:

1. **Optimistic locking** (`@Version` in JPA) — Let both proceed, detect conflict on save
2. **Pessimistic locking** (`SELECT ... FOR UPDATE`) — Lock the row, second request waits
3. **Database unique constraint** — Prevent duplicate at DB level
4. **Redis distributed lock** — Lock before checking availability

Expected concurrent bookings per slot: very low (1-2 conflicts at most for a portfolio project).

## Decision
Use **optimistic locking** as the primary mechanism, with a **database unique constraint** as a safety net.

```java
@Entity
public class Booking {
    @Id @GeneratedValue
    private Long id;

    @Version
    private Long version;

    // service_id + booking_date + start_time = unique
}
```

Plus a unique constraint:
```sql
ALTER TABLE bookings
ADD CONSTRAINT uq_booking_slot UNIQUE (service_id, booking_date, start_time);
```

## Consequences

### Positive
- **Non-blocking** — No threads wait. Better throughput for read-heavy workload.
- **Spring built-in** — `@Version` annotation handles it automatically. `OptimisticLockException` is caught and retried.
- **Database constraint as backstop** — Even if the application logic fails, the database prevents the duplicate.
- **Simple to implement and explain** — Easy to discuss in interviews.

### Negative
- **Retry overhead** — On conflict, the losing request must retry. Acceptable at this scale.
- **Not suitable for high contention** — If 100 users book the same slot simultaneously, optimistic locking creates many retries. For this project (and most real-world services), this is not realistic.
- **Application must handle `OptimisticLockException`** — Needs a retry mechanism or user-facing error message.

### Retry Strategy
```java
@Retryable(OptimisticLockException.class)
public Booking createBooking(CreateBookingRequest request) {
    // Check availability, create booking
}
```
Max 3 retries, then return 409 Conflict to the customer.
