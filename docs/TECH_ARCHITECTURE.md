# ROMS Backend — Technical Architecture

**Scope:** Shared backend repository (5 dev)  
**Style:** Spring Boot Three-Layer Architecture, **Package by Layer**  
**Sources:** `PRD.md` for business rules, `API_CONVENTIONS.md` for HTTP contracts, Flyway migrations for schema.

## 1. Stack and Dependency Status

| Concern | Decision |
|---|---|
| Language / build | Java 21 / Maven |
| Framework | Spring Boot 4.1.1, Spring Web MVC |
| Database | PostgreSQL + Spring Data JPA / Hibernate |
| Schema migrations | Flyway (`flyway-core`, `flyway-database-postgresql`) |
| Auth | Spring Security + JJWT (HS256); BCrypt passwords; hashed opaque refresh tokens |
| Realtime | Spring WebSocket + STOMP |
| AI integration | Gemini API, menu-scoped assistance |
| Temporary data | Redis for forgot-password OTP and Gemini menu context only |
| Menu image hosting | Cloudinary; PostgreSQL stores image URLs/references in `menu_item_images` |
| Password-recovery email | Gmail SMTP using Spring Boot Mail (`JavaMailSender`) |
| Existing helpers | Jakarta Validation, Lombok, Actuator, Spring Boot test starters |

**Installed vs planned:** Web MVC, JPA, PostgreSQL, Flyway, Spring Security, Validation, Lombok and Actuator are in the submitted `pom.xml`. JJWT, WebSocket and Redis starters, and Gemini SDK/client are not yet configured. JJWT with HS256 is approved for auth; verify a compatible version before adding. Other dependencies require team approval. No rate limiting in current scope.

## 2. Architecture and Allowed Dependencies

```text
HTTP / REST Controller (Presentation)
                |
         Service interface
                |
      ServiceImpl (Business)
                |
       JpaRepository (Data)
                |
            PostgreSQL
```

- **Controller:** handles transport, DTO validation, HTTP status and delegation; **never** calls repositories or contains business rules.
- **Service interface:** declares business operations; **ServiceImpl** implements use cases, role-sensitive checks and transactions.
- **Repository:** persistence/query contracts; no workflow decisions or HTTP responses.
- **Entity / DTO / Mapper:** supporting types, **not fourth/fifth layers**.
- One service may call another service for a reusable business operation; avoid circular service dependencies and unrelated cross-feature edits.
- Security, STOMP, Redis and Gemini are infrastructure/integration concerns; business workflow still enters the service layer.
- Return DTOs to clients, never expose JPA entities directly or rely on frontend validation alone.

## 3. Package Structure and Naming

Use the application's **existing base package** (inspect `*Application.java`, do not assume a new base package):

```text
<base-package>/
├── controller/             OrderController
├── service/                OrderService (interface)
│   └── impl/               OrderServiceImpl
├── repository/             OrderRepository
├── entity/                 Order, OrderItem
├── dto/
│   ├── request/            CreateOrderRequest
│   └── response/           OrderResponse
├── mapper/                 OrderMapper
├── enums/                  OrderStatus, OrderItemStatus
├── security/               authentication/JWT components
├── websocket/              STOMP config, notification adapters (when implemented)
├── integration/            Gemini client adapter (when implemented)
├── config/                 framework configuration
├── exception/              domain exceptions, GlobalExceptionHandler
└── common/                 truly shared utilities/types only
```

- Organize packages **by layer**, not `order/controller`, `menu/service`, etc.
- Use singular Java class names: `OrderController`, `OrderService`, `OrderRepository`, `OrderMapper`.
- Keep request and response DTOs separate; avoid generic `Map<String,Object>` business payloads.
- Create optional packages/classes only when used; do not generate boilerplate with no purpose.

## 4. Controller and DTO Rules

- Prefer thin `@RestController` classes with constructor-injected service interfaces.
- Receive validated request DTOs (`@Valid`); use path/query values only for identifiers, filters and pagination.
- Keep entity lookup, pricing, permission-sensitive decisions and state transitions in services.
- Never accept client-supplied authoritative values such as `createdBy`, server timestamps, derived totals, or trusted statuses without server checks.
- Return dedicated response DTOs and agreed HTTP status codes; consult `API_CONVENTIONS.md` for response envelopes, errors and pagination.
- Use Bean Validation for shape/field rules; service-level checks for database/current-state business constraints.

## 5. Service Pattern and Transactions

- Every business use case is exposed through a `*Service` interface and implemented in `service.impl.*ServiceImpl`.
- Use **constructor injection**; avoid field injection and static access to Spring beans.
- Put `@Transactional` on public service operations that change related persistent state; use `@Transactional(readOnly = true)` for appropriate read-only service operations.
- Consider Spring proxy behavior: do not assume an internal self-call activates a different `@Transactional` annotation.
- Keep multi-step updates atomic, especially orders/order items and ingredient balance/stock movements.
- Call another service when it owns a business rule; avoid bypassing its invariants by writing to its repositories directly.
- After mutating data, map the committed/consistent outcome to a response DTO; emit external notifications only **after commit**.
- Handle concurrent writes deliberately (e.g. optimistic `@Version` or an appropriate database lock) in tasks where races can violate invariants; choose the technique per use case, not globally.

## 6. Repository and JPA Conventions

- Use `interface XRepository extends JpaRepository<X, Long>`; V1 uses `BIGSERIAL` primary keys mapped to Java `Long`.
- Prefer derived query methods for straightforward lookups; use `@Query` for complex, documented queries.
- Do **not** create repository implementations for routine CRUD already supplied by Spring Data JPA.
- Repositories return entities, projections or `Optional` as appropriate; not HTTP response DTOs.
- Map foreign keys through JPA relationships where useful; ensure column names and constraints match **applied** Flyway SQL.
- Default to **lazy** loading for to-many associations and avoid exposing bidirectional entity graphs to JSON.
- Avoid N+1 queries for list/detail reads using task-appropriate fetch joins, entity graphs or DTO projections; do not make everything eager.
- Use `BigDecimal` for money and precise stock quantities; use Java enum mappings consistent with SQL string constraints.
- Database integrity constraints complement service validation; never rely on application checks alone for race-sensitive uniqueness.

## 7. Mapper Pattern

- Place mapper classes in `mapper/`; use **manual mapping** until a mapper dependency is explicitly approved.
- Convert Request DTO → Entity/update fields only after service has applied authorized defaults and rules.
- Convert Entity → Response DTO in service workflows; avoid accidental lazy-loading outside transactions.
- Never copy client-controlled IDs, status, prices or audit fields into persistent entities by blindly mapping all fields.
- Keep business calculations and status transitions in services, **not** in mappers.
- Use named mapping methods (`toResponse`, `toEntity`, `updateEntity`) consistently, tailored to each operation.

## 8. Validation, Errors and HTTP Contract

- Centralize exception translation with `@RestControllerAdvice`; never leak stack traces, tokens or SQL details.
- Map invalid request data to **400**, unauthenticated calls to **401**, forbidden access to **403**, missing resources to **404**, and invalid current-state transitions to **409**.
- Use specific domain exceptions for meaningful cases; do not swallow exceptions or return null to indicate a business failure.
- Use the approved `ApiResponse<T>` and `PageResponse<T>` contracts in `API_CONVENTIONS.md`; endpoint-specific DTOs/paths still require coordination where not yet defined.

## 9. Authentication and Token Security

- Use Spring Security + **JJWT** with **HS256**; load a cryptographically random signing secret of at least 256 bits from environment configuration, never from source control. Access JWT lifetime is **15 minutes** for every role.
- Access JWT claims: `sub` (user ID), `role`, `sid` (stable `refresh_tokens.id`), `iat`, and `exp`. `jti` is not required. Resolve `TABLE_ACCOUNT` table assignment from `users.table_id`, not a client-supplied value or JWT `tableId`.
- Passwords use **BCrypt**. Generate high-entropy random opaque refresh tokens and store only **SHA-256 hashes** in PostgreSQL `refresh_tokens`; Redis is not used for sessions/tokens.
- Refresh lifetimes: **90 days** for `TABLE_ACCOUNT`, **7 days** for staff/Owner; use sliding expiration at each successful refresh. There is **no absolute session lifetime cap**.
- **One row of `refresh_tokens` represents one stable login session.** Refresh atomically verifies the current hash, replaces `token_hash`, and extends `expires_at` on that same row; rotate on every use. Serialize concurrent refreshes (e.g. row lock), rejecting a reused/expired/revoked token. The client should issue only one refresh request at a time.
- `TABLE_ACCOUNT`: at most **one active session** per account. In a transaction, serialize logins for that user and revoke old sessions before creating the replacement; the previous session's access JWT must immediately stop working.
- Staff/Owner: **multiple sessions** allowed; logout revokes only the current session. **No public logout-all endpoint.** Expose an internal `revokeAllSessions(userId)` operation for password changes/resets or account deactivation.
- Validate the JWT signature/expiry, account status, and `sid` against the active (not revoked) session in PostgreSQL on authenticated requests. This deliberately simple approach makes revocation effective immediately, including staff JWTs after password reset/deactivation; no token blacklist or Redis session store is needed.
- React keeps access tokens in memory, sends them with `Authorization: Bearer`, and receives refresh tokens in an **HttpOnly cookie** (`Secure` on HTTPS, narrowly scoped `Path=/api/auth`, and deployment-appropriate `SameSite`). Never store refresh tokens in localStorage or return them in JSON.
- Allowlist configured frontend origins with credentialed CORS. Protect cookie-based auth requests against CSRF using Origin validation plus appropriate SameSite; if deploying cross-site with `SameSite=None`, add explicit CSRF token protection. Local HTTP development may use a non-Secure cookie only in the dev profile.
- Public auth endpoints are `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, and `GET /api/auth/me`; no `/api/auth/logout-all`. Request/response contracts follow `API_CONVENTIONS.md`.
- `device_id` is optional app-managed metadata, not proof of a physical device. For password recovery, generate a cryptographically random 6-digit OTP, send it via Gmail SMTP, and store only a verifier/hash and attempt state in Redis with a **5-minute TTL** and **maximum 5 failed verification attempts per issued OTP**. Invalidate after success or attempt exhaustion; do not disclose account existence in public recovery responses.
- Keep secrets out of version control and enforce WebSocket subscription security separately from REST authorization.

## 10. WebSocket / STOMP Realtime

- Use REST for **commands and persistence**; STOMP transports notifications about relevant committed changes.
- Publish notifications **after successful database commit**, not before (including order/item, kitchen, table/session updates).
- Clients should treat notifications as signals to refresh or reconcile data; PostgreSQL is authoritative.
- For initial **single-instance** deployment, Spring's simple STOMP broker is sufficient; Redis is not the STOMP broker.
- STOMP endpoint: `/ws`. Client application destination prefix: `/app`. Broker destination prefixes: `/topic`, `/queue`, `/user`.

### 10.1 Approved Destinations and Subscription Authorization

| Destination | Type | Allowed Roles | Event Description | Event DTO |
|---|---|---|---|---|
| `/topic/kitchen/orders` | Topic | `KITCHEN_STAFF`, `OWNER` | New order placed, order cancellation | `KitchenOrderEvent`, `OrderStatusChangedEvent` |
| `/topic/session/{sessionId}/orders` | Topic | `SERVICE_STAFF`, `OWNER`, `TABLE_ACCOUNT` (own table only) | Order status change, dish cooked/ready/completed | `OrderStatusChangedEvent`, `OrderItemStatusChangedEvent` |
| `/topic/tables` | Topic | `SERVICE_STAFF`, `OWNER` | Session open/close, table transfer | `SessionStatusChangedEvent`, `TableTransferEvent` |
| `/user/queue/notifications` | Queue | Authenticated user | Personal notifications | User notification payload |

- Subscription authorization rule: `TABLE_ACCOUNT` is strictly verified against its assigned table (`users.table_id == session.table_id`). Subscriptions to foreign sessions are rejected.
- Event DTOs reside in `com.roms.dto.event`; destination definitions and authorization rules in `com.roms.websocket.StompDestinations`.

## 11. Gemini and Redis

- Gemini recommendations use a **menu/ingredient context**, not direct SQL access or authority to create/change orders.
- Redis caches stable information: menu item IDs, names/descriptions, category and recipe ingredient context.
- Invalidate cached context **after committed** changes to menu data, categories, recipe assignments or ingredient descriptions.
- **Do not cache current availability** as part of Gemini's stable menu context. Determine live sellability using `menu_items.status`, `recipe_items` and `ingredients.current_stock` in PostgreSQL; `available_quantity` is removed.
- Validate Gemini-returned item IDs and live recipe-based sellability before returning recommendations; recheck under concurrency control when creating an Order.
- Cache misses rebuild from PostgreSQL; cache outages must not cause stale or unsafe availability decisions.
- Redis also stores expiring forgot-password OTP verification data; **not** order state, inventory balances, refresh tokens or rate-limiting counters.
- Choose Redis keys, TTL and Gemini request/response schema in approved implementation tasks before writing integrations.

## 11.1 Menu Images — Cloudinary

- Store menu image files in **Cloudinary**, not PostgreSQL or the application server filesystem. Persist only image URL/reference and metadata supported by the applied `menu_item_images` schema.
- Authorized menu management operations handle uploads and removals via a backend Cloudinary integration; validate file type and size, and do not expose Cloudinary API secrets to React.
- Keep Cloudinary cloud name/API credentials in environment or secret configuration, never in source control. Coordinate file-upload API contracts and Cloudinary asset cleanup with Member 3; do not add schema columns or introduce public upload endpoints without an approved contract.

## 11.2 Password-Recovery Email — Gmail SMTP

- Use Spring Boot Mail (`JavaMailSender`) with Gmail SMTP for forgot-password OTP delivery; secure SMTP credentials (e.g. an app password where supported) belong in environment variables, never source control.
- Quang owns OTP business workflow and mail sending integration; the existing shared Redis OTP primitives remain under BINH-DEV ownership. Coordinate compatibility with those primitives before implementation.
- Redis stores a secure OTP verifier/hash, expiry and failed-attempt count; OTP is 6 digits, expires after 5 minutes, and permits a maximum of 5 failed attempts per issued code. Consume the OTP after successful verification and avoid account-enumeration responses.
- Do not send email inside an uncommitted state-changing transaction when it could cause inconsistency; handle delivery failure without reporting a successful send. Specific forgot/reset endpoint DTOs remain subject to approved API contracts.

## 12. Order / Kitchen Consistency

- **Order:** `PENDING → COOKING → COMPLETED`, and `PENDING → CANCELLED`; **no** order-level `READY` or `REJECTED`.
- **OrderItem:** `PENDING → COOKING → READY → COMPLETED`, and `PENDING → CANCELLED`.
- Cancel an Order **only if Order is PENDING**; cancel an OrderItem **only if that item is PENDING**.
- An Order remains `COOKING` while any non-cancelled item is unfinished, even if some items are `COMPLETED`.
- Mark the Order `COMPLETED` only if **all non-cancelled items are COMPLETED** and **at least one non-cancelled item exists**.
- Check state and update parent/child in the same transaction; guard concurrent status changes. Send STOMP events after commit.
- Authoritative business rules and edge cases remain in `PRD.md`; do not invent extra transition paths.
- **Reservation is out of scope.** Do not add reservation code, APIs or database tables.

### Order Immutability and Inventory Consistency

- OrderItems and quantities are immutable after Order creation; additional dishes require a new Order within the same active Service Session.
- **On Order creation**: aggregate ingredient requirements across all requested OrderItems using recipes; check sufficient stock and menu sellability, then atomically persist Order/OrderItems, deduct ingredient balances and append negative `ORDER_CONSUMPTION` movements linked to each OrderItem. If anything is insufficient, reject with `409 Conflict` and roll back all changes. Never allow negative stock.
- **On kitchen acceptance (`PENDING → COOKING`)**: change the Order state only; do not deduct ingredients again.
- **On cancellation** of an Order or OrderItem while the respective target is `PENDING`: restore only the quantities previously deducted for affected OrderItems, derived from recorded consumption movements; append positive compensating `ORDER_CONSUMPTION` movements and update stock in the same transaction. Prevent double-restoration even for concurrent requests; do not recalculate from a changed recipe.
- Use a consistent ingredient-locking order (or equivalent concurrency-safe updates) during create/cancel and inventory mutations to avoid overselling and lost updates. No stock reservation or `available_quantity` logic is needed.
- Treat out-of-stock as a derived availability condition from recipes and stock, not an automatic mutation of `menu_items.status`; Kitchen/Owner can still manually set `TEMPORARILY_UNAVAILABLE`.
- Cancelled OrderItems remain persisted but are excluded from normal active-order views.
- Purchase creation, purchase corrections, and inventory adjustments must atomically update stock balances and append stock movement records.
- Stock movement history is append-only; corrections must not modify or delete historical movements.
- Prevent lost updates during concurrent inventory operations using appropriate locking or versioning.
- Purchase correction logic must respect existing Flyway constraints and preserve inventory traceability.

## 13. Database and Migrations

- V1: `src/main/resources/db/migration/V1__init_schema.sql`; apply approved V2 order status changes as a separate migration if V1 was applied/shared.
- The **whole ordered Flyway migration history** is the schema source of truth. No duplicate `DATABASE_SCHEMA.md`.
- Never edit already applied/shared migrations; create a new versioned SQL migration and coordinate numbers across 5 branches.
- Target `spring.jpa.hibernate.ddl-auto=validate`; the submitted configuration currently uses `update`, so verify migrations/entity alignment before switching.
- Use DB constraints, indexes and transactions where they protect integrity; align JPA mappings with database reality, not guessed schemas.

## 14. Testing and Team Workflow

- Unit-test service rules: order/item state transitions, PENDING-only cancellations and stock restoration, insufficient stock on order creation, concurrent orders, kitchen acceptance without a second deduction, mixed completed items, zero active items and authorization decisions.
- Integration-test JPA queries, Flyway migrations, transaction behavior, security and concurrent state changes where relevant.
- Test STOMP after-commit signaling, OTP expiry and cache invalidation during their respective tasks.
- Developers work in separate Git branches; review migration versions and shared DTO/API contract changes before merging.
- Change only assigned scope; update your own rows in `TASKS.md` **after verifying** acceptance criteria. Record blocked tasks honestly.
- If PRD, schema, API contract and architecture disagree, report the conflict; never let an AI agent silently choose a rule.

**Still requires decisions before relevant implementation:** deployment-specific FE/BE origins and cookie/CSRF configuration, Gemini payload contract, and exact image-upload and forgot/reset API DTOs/endpoints. Cloudinary image hosting and Gmail SMTP email delivery (6-digit OTP, 5-minute TTL, 5 failed attempts) are approved. JWT library/algorithm/claims, auth endpoints, token lifetimes, rotation, session policies, API response/pagination envelopes, Redis cache/OTP primitives, and STOMP destinations/event contracts are approved.
