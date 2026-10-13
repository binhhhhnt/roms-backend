# ROMS Backend — Shared Task Board

**Scope:** Backend only (five developers). **Architecture:** Spring Boot 3-Layer, Package by Layer.  
**Status:** `Todo` → `In Progress` → `In Review` → `Done`; use `Blocked` when prerequisites/approvals are missing.

## 1. Shared Foundation — BINH-DEV

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-001 | Verify PostgreSQL, Flyway V1 and V2 status constraints | BINH-DEV | Done | Inspect applied migrations and data; V2 must exclude `REJECTED`/Order `READY`, allow OrderItem `COMPLETED`; do not edit applied migrations |
| BE-028 | Create and validate all JPA Entities + Enums from applied Flyway migrations | BINH-DEV | Done | BE-001; `Long` keys, correct FK mappings, `TABLE_ACCOUNT` role; no invented schema |
| BE-029 | Set up shared Spring Boot package/config foundation and Entity mapping tests | BINH-DEV | Done | BE-001, BE-028; preserve Controller → Service → Repository |
| BE-002 | Finalize common API response, errors and pagination conventions | BINH-DEV | Done | Requires team approval; follow `API_CONVENTIONS.md` |
| BE-004 | Implement global exception handling and shared validation/error infrastructure | BINH-DEV | Done | BE-002 |
| BE-003 | Finalize JWT, refresh-token hashing, password hashing and auth contracts | BINH-DEV | Done | Requires agreement with QUANG and approved dependencies |
| BE-006 | Implement Spring Security/JWT foundation and hashed refresh-token storage | BINH-DEV | Done | BE-003, BE-028; coordinate user-account contracts with BE-005 |
| BE-030 | Configure shared Redis connection and OTP/menu-cache primitives | BINH-DEV | Done | Approved Redis dependency; configured RedisConfig, OtpRedisService, MenuContextCacheService, application properties, and unit tests (58/58 tests passed); no rate limiting |
| BE-020 | Define STOMP destinations, event contracts and subscription authorization | BINH-DEV | Done | Defined STOMP destinations, event DTO contracts, subscription authorization rules, documented in TECH_ARCHITECTURE.md Section 10, unit tests passed (69/69 total tests) |
| BE-031 | Set up WebSocket/STOMP shared infrastructure and after-commit event publishing utilities | BINH-DEV | Done | BE-020; configured WebSocketConfig (/ws, SockJS, CORS), StompEventPublisher (after-commit & immediate), WebSocketAuthChannelInterceptor (JWT CONNECT & subscription auth), unit tests passed (84/84 total tests) |
| BE-032 | Configure Gemini API client/integration foundation | BINH-DEV | Done | Configured GeminiConfig (RestClient, timeout, baseUrl), GeminiClientAdapter/Impl, Gemini DTOs, .env GEMINI_API_KEY placeholder, unit tests passed (90/90 total tests); no AI feature logic |

## 2. Authentication, Users and AI — Business Owners

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-005 | User/account management and authorization for five roles | QUANG | Todo | BE-028, BE-003, BE-006; `TABLE_ACCOUNT`, no roles/permissions tables; related ROMS-008–010 |
| ROMS-001 | Login/authentication endpoint integration | BINH-DEV | Done | BE-006; includes token issuance/refresh/logout as approved by contracts |
| BE-018 | Agree on forgot-password OTP delivery, expiry and retry workflow | QUANG | Todo | Coordinate Redis keys with BINH-DEV; requires team decisions |
| BE-019 | Implement forgot/reset password with Redis TTL and hashed OTP verifier | QUANG | Todo | BE-006, BE-018, BE-030; related ROMS-002–003 |
| ROMS-004–006 | Change password, profile and authorization support APIs | QUANG | Todo | BE-005, BE-006; only applicable backend work |
| BE-022 | Define Gemini assistant API contract and menu-context format | QUANG | Todo | BE-007, BE-032; approved API/AI requirements |
| BE-023 | Implement Redis Gemini menu/recipe context caching and invalidation | QUANG | Todo | BE-007, BE-014, BE-022, BE-030; invalidate after commit; no live availability cache |
| BE-024 | Implement Gemini recommendations with live recipe/stock sellability and ID validation | QUANG | Todo | BE-008, BE-022–023; does not modify orders; no serving quota; related ROMS-032 |

## 3. Menu and Table/Session Features

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-007 | Implement categories, menu item CRUD, images and tablet menu read APIs | Member 3 | Todo | BE-028; ROMS-019–023, ROMS-025–026 |
| BE-008 | Implement manual menu status changes and recipe/stock-based sellability checks | BINH-DEV | Done | Implemented PATCH /api/menu-items/{id}/status (OWNER/KITCHEN_STAFF, 403 on DISCONTINUED for non-owner, Redis Gemini cache invalidation after commit) and GET /api/menu-items/{id}/sellability (live PostgreSQL recipe/stock check, no available_quantity/serving quota); 21/21 tests passed (111/111 project tests passed) |
| BE-009 | Implement tables and service session APIs, including open/close and history | QUANG | Todo | BE-028, BE-006; ROMS-011–015, 017–018, 024, 036 |
| BE-012 | Implement table transfer and transfer-history operations | QUANG | Todo | BE-009; ROMS-016 |

## 4. Ordering and Kitchen — BINH-DEV

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-010 | Implement Order/OrderItem creation, queries, atomic recipe-stock deduction and PENDING-only cancellation/restoration | BINH-DEV | Done | Implemented POST /api/orders (atomic stock deduction, negative ORDER_CONSUMPTION movements, 409 if insufficient), GET /api/orders, GET /api/orders/{id}, POST /api/orders/{id}/cancel & POST /api/orders/{id}/items/{itemId}/cancel (restores stock from history, positive compensating movements, double-restoration protection, STOMP event); 15/15 tests passed (126/126 project tests passed) |
| BE-011 | Implement kitchen acceptance, cooking, ready/served item workflow and Order completion | BINH-DEV | Done | Implemented GET /api/kitchen/orders, GET /api/kitchen/orders/{id}, POST /api/kitchen/orders/{id}/accept (no stock re-deduction, 409 if not PENDING, STOMP event) and PATCH /api/kitchen/orders/{id}/items/{itemId}/status (PENDING->COOKING->READY->COMPLETED, role-based checks, auto parent order completion, zero-active items protection); 11/11 tests passed (137/137 project tests passed) |
| BE-021 | Publish and authorize relevant Order/Kitchen STOMP updates after DB commit | BINH-DEV | Done | Implemented dedicated OrderEventPublisher with afterCommit transaction synchronization; publishes KitchenOrderEvent to /topic/kitchen/orders and OrderStatusChangedEvent to /topic/session/{sessionId}/orders on creation; publishes OrderStatusChangedEvent and OrderItemStatusChangedEvent to both kitchen and session destinations on status updates; 6/6 tests passed (143/143 project tests passed) |
| BE-027 | Test transitions, stock deduction/restoration, insufficient stock and concurrent Order creation/cancellation | BINH-DEV | Done | Implemented OrderConcurrencyIntegrationTest (multi-threaded race conditions with pessimistic row locking, idempotent double-restore protection, end-to-end lifecycle, zero-active-items, mixed completion) plus unit edge cases in OrderServiceImplTest and KitchenServiceImplTest; 154/154 project tests passed |

> Cart interactions (ROMS-027), success screens and other UI-only parts belong to the separate React project. Backend covers order validation, creation and retrieval required by those tickets.

## 5. Inventory, Recipes and Purchases — Member 4

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-014 | Ingredient management, current/minimum stock, stock alerts, inventory checks/adjustments and movement history | Member 4 | Todo | BE-028; ROMS-043–044, 048–049; nonnegative balances and immutable movements; coordinate stock transaction/repository operations with BE-010 |
| BE-033 | Recipe/BOM management and ingredient-cost queries | Member 4 | Todo | BE-007, BE-014; ROMS-051–054; `recipe_items` uses `ingredients.unit` |
| BE-015 | Purchase list/create/detail and correction processing | Member 4 | Todo | BE-014; ROMS-057–059; purchased/received goods only; correction must atomically update stock and append traceable movements; review V1 movement constraints |

## 6. Expenses, Reports and Dashboard — Member 5

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-026 | Expense list/create/update/cancel APIs | Member 5 | Todo | BE-028; ROMS-061–062; preserve cancellation audit (`cancelled_at`) |
| BE-016 | Dashboard/reporting queries and exports | Member 5 | Todo | Relevant operational data; ROMS-007, ROMS-063–067; payments/revenue calculations only as supported by approved schema/requirements |

## 7. Integration and Team Verification

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-025 | Verify OTP, cache invalidation, STOMP authorization/delivery and Gemini availability | QUANG | Todo | BE-019, BE-021, BE-024; integration review with BINH-DEV |
| BE-017 | Run cross-feature integration tests, schema/API contract review and release readiness | BINH-DEV | Todo | BE-025, BE-027 plus feature-owner verification; each owner tests their own feature |

**Reserved ID:** `BE-013` (removed Reservation functionality; do not reuse).

## Update Protocol

- The named owner maintains their rows. `Todo` does not imply a task has not been partially implemented; verify before updating.
- Dependencies are prerequisites, not a strict sprint/merge order. Work may proceed in parallel where safe.
- Team-approval items remain `Blocked` if agreements are not finalized; ownership identifies the coordinator, not unilateral authority.
- Mark `Done` only after code review and relevant tests; include PR and evidence in the note when useful.
- Keep original `BE-xxx` IDs and feature-ticket references stable; add new IDs by team agreement.
