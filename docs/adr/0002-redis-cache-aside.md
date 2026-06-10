# ADR 0002: Redis Cache-Aside Pattern

## Status
Accepted

## Context
The service catalog (listing, search, detail pages) is read-heavy. Direct PostgreSQL queries for every request will be slow under load. We need a caching strategy.

Options:
1. **Cache-aside** (lazy loading) — Application checks cache first, on miss loads from DB and writes to cache
2. **Write-through** — Application writes to cache and DB simultaneously
3. **Read-through** — Similar to cache-aside but cache provider handles DB reads
4. **No caching** — Simplest, but doesn't demonstrate caching knowledge

## Decision
Use **cache-aside** pattern with Redis.

```
Application flow:
1. GET request for service data
2. Check Redis: GET service:{id}
3. Cache HIT → return data (fast path)
4. Cache MISS → query PostgreSQL → write to Redis with TTL → return data
```

## Consequences

### Positive
- **Explicit control** — Application decides what to cache and when. Clear, easy to reason about.
- **Redis skill** — Directly demonstrates Redis knowledge, which is listed on CV.
- **TTL-based expiration** — Data eventually consistent. Simple invalidation model.
- **Selective caching** — Only cache read-heavy endpoints, not everything.

### Negative
- **Cache stampede** — If a popular key expires, many requests hit DB simultaneously. Mitigate with lock-based regeneration or staggered TTLs.
- **Manual invalidation** — When a vendor updates a service, application must explicitly delete the cache key. Forgetting = stale data.
- **Extra application code** — Every read needs the check-then-load pattern. Spring Cache annotations (`@Cacheable`, `@CacheEvict`) reduce boilerplate.

### Invalidation Strategy
- **Service CRUD**: Evict `service:{id}` and `services:category:{categoryId}` on any change
- **Vendor update**: Evict `vendor:{id}` and all associated service cache keys
- **TTL fallback**: Maximum 5-15 minutes staleness even if invalidation fails
