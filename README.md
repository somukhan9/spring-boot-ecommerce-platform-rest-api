# E-Commerce Microservice Platform

Spring Boot 3 · Java 17 · PostgreSQL 16 · Spring Cloud Gateway · JWT + rotating refresh tokens · RBAC · Maven multi-module

```
                        Internet
                           │  :8080 (only published port)
                    ┌──────▼───────┐
                    │ api-gateway  │  rate limit → path hygiene → verify JWT → coarse RBAC
                    └──────┬───────┘  → swap JWT for 60s gateway-signed X-Internal-Token
        ───────── internal docker network (no host access) ─────────
          ┌────────────────┼─────────────────┐
   ┌──────▼──────┐  ┌──────▼───────┐  ┌──────▼──────┐
   │auth-service │  │product-service│◄─│order-service│  (order → product via /internal/**)
   └──────┬──────┘  └──────┬───────┘  └──────┬──────┘
       authdb           productdb          orderdb      (separate DB + DB role per service)
```

| Module | Purpose |
|---|---|
| `common-lib` | Token services, `InternalAuthFilter`, shared security baseline, error handling |
| `auth-service` (8081) | Register / login / refresh / logout, user + role admin |
| `product-service` (8082) | Catalog, ownership-aware writes, atomic stock reserve/release |
| `order-service` (8083) | Orders, price snapshots, stock saga with compensation |
| `api-gateway` (8080) | Single public entry point |

## Security model (defence in depth)

**Authentication**
- **Access token**: JWT (HS256), 15 min, claims `sub`, `email`, `roles`. Signed by auth-service, verified **only** by the gateway.
- **Refresh token**: opaque 384-bit random string, 7 days, stored **as SHA-256 hash**. **Rotated on every use**; presenting an already-used token is treated as theft and revokes the whole token family. Logout revokes the family; `logout-all` revokes every session. Changing a user's roles or disabling them revokes their refresh tokens.

**Protecting individual services from direct calls**
1. **Network**: services have no published ports and sit on an `internal: true` Docker network. Postgres is also unreachable from outside.
2. **Gateway-signed internal token**: after validating the user, the gateway removes `Authorization` and any client-supplied `X-Internal-Token`, and adds a fresh 60 s token signed with `INTERNAL_SECRET`. Every service runs `InternalAuthFilter` first: **no valid internal token ⇒ 403**, even if the attacker holds a perfectly valid user JWT.
3. **Secret separation**: business services never get `JWT_SECRET`, so a compromised service cannot mint user tokens.
4. **`/internal/**` is never routed** by the gateway (and is explicitly rejected, along with `..`, `//`, `;`, `%2e`, `%2f` bypass attempts).
5. **Per-service database credentials**: one DB and one role per service.

**RBAC** – roles `ADMIN`, `SELLER`, `CUSTOMER`
| Action | Roles |
|---|---|
| Browse products | public |
| Create / edit / delete product | `SELLER` (own products only) or `ADMIN` |
| Place order, list own orders | `CUSTOMER` |
| View / cancel an order | owner or `ADMIN` |
| List all orders, change order status | `ADMIN` |
| List users, change roles, enable/disable | `ADMIN` |

Enforced twice: coarse at the gateway (`/api/admin/**`), fine-grained in services via `@PreAuthorize` plus ownership checks. New registrations are always `CUSTOMER`; only an ADMIN can grant `SELLER`/`ADMIN`.

## Run it

```bash
cp .env.example .env          # edit secrets (openssl rand -base64 48)
docker compose up --build
```
Gateway: `http://localhost:8080`. The first ADMIN is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

Without Docker: start Postgres, create the 3 databases/roles from `docker/init-db.sh`, export the env vars from `.env`, then `mvn clean package` and run each jar.

## Try it

```bash
GW=http://localhost:8080

# admin login
curl -s $GW/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@shop.local","password":"ChangeMe!12345"}'
# -> {"accessToken":"...","refreshToken":"...","tokenType":"Bearer","expiresIn":900}

# register a user, then promote to SELLER as admin
curl -s $GW/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"seller@shop.local","password":"Passw0rd!123","fullName":"Sam Seller"}'
curl -s $GW/api/admin/users -H "Authorization: Bearer $ADMIN_TOKEN"            # find the id
curl -s -X PUT $GW/api/admin/users/$SELLER_ID/roles -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' -d '{"roles":["SELLER","CUSTOMER"]}'

# seller logs in again (roles refreshed), creates a product
curl -s $GW/api/products -H "Authorization: Bearer $SELLER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Mechanical Keyboard","description":"75% layout","price":89.90,"stock":25}'

# customer places an order
curl -s $GW/api/orders -H "Authorization: Bearer $CUSTOMER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":"<uuid>","quantity":2}]}'

# refresh (old refresh token is now dead; replaying it kills the whole session family)
curl -s $GW/api/auth/refresh -H 'Content-Type: application/json' -d '{"refreshToken":"<token>"}'
```

Prove the isolation (from inside the Docker network a direct hit is refused, from the host the port is closed):
```bash
docker compose exec api-gateway sh -c 'wget -qO- --header="Authorization: Bearer $USER_JWT" http://order-service:8083/api/orders/my'
# -> 403 "Direct access to this service is not allowed"
```

## API summary

| Method & path | Access |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | public |
| `POST /api/auth/logout-all`, `GET /api/auth/me` | any logged-in user |
| `GET/PUT /api/admin/users…` | ADMIN |
| `GET /api/products?q=&page=&size=`, `GET /api/products/{id}` | public |
| `POST/PUT/DELETE /api/products…` | SELLER (own) / ADMIN |
| `POST /api/orders`, `GET /api/orders/my` | CUSTOMER |
| `GET /api/orders/{id}`, `POST /api/orders/{id}/cancel` | owner / ADMIN |
| `GET /api/orders`, `PATCH /api/orders/{id}/status` | ADMIN |

## Before going to production
- Replace `ddl-auto: update` with Flyway/Liquibase migrations.
- Terminate TLS in front of the gateway; consider mTLS between services as well.
- Move secrets to a secret manager. Consider RS256 for access tokens (private key only in auth-service) and rotating keys.
- Rate limiter is in-memory per gateway instance → use Redis-backed `RequestRateLimiter` when running replicas.
- Order/stock flow is a simple compensating saga; use an outbox + message broker for stronger guarantees.
- Add service discovery / config server and tracing (Micrometer + OTel) as the platform grows.
- Add tests (Testcontainers for Postgres is a good fit).
