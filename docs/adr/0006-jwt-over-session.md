# ADR 0006: JWT over Session-Based Authentication

## Status
Accepted

## Context
We need an authentication mechanism for the REST API. Options:
1. **JWT (JSON Web Token)** — Stateless, client stores token, server validates signature
2. **Server-side sessions** — Server stores session state, client holds session ID cookie

The frontend is a React SPA making API calls. No server-rendered pages.

## Decision
Use **JWT** with access token + refresh token pair.

- Access token: short-lived (15 minutes), stored in memory (frontend)
- Refresh token: long-lived (7 days), stored in HttpOnly cookie
- On access token expiry, frontend uses refresh token to get a new one

## Consequences

### Positive
- **Stateless** — No session storage needed on server. Scales horizontally without sticky sessions.
- **RESTful** — No server-side state aligns with REST principles.
- **Industry standard for SPAs** — What most companies use for React/Vue frontends.
- **Demonstrates security awareness** — Refresh token rotation, HttpOnly cookies, short-lived access tokens.

### Negative
- **Token revocation is hard** — Can't easily invalidate a token before expiry. Mitigate with short access token TTL (15 min) and a token blacklist in Redis for critical cases (logout, password change).
- **XSS risk** — If access token is in localStorage, XSS can steal it. Mitigate by storing in memory only, not localStorage.
- **More complex than sessions** — Token refresh flow adds frontend complexity.

### Token Structure
```json
// Access Token (JWT payload)
{
  "sub": "user_id",
  "email": "user@example.com",
  "role": "CUSTOMER",
  "exp": 1718000000
}
```
