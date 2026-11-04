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
| BE-005 | User/account management and authorization for five roles | QUANG | Done | BE-028, BE-003, BE-006; `TABLE_ACCOUNT`, no roles/permissions tables; related ROMS-008–010; UserController & UserService fully implemented and tested |
| ROMS-001 | Login/authentication endpoint integration | BINH-DEV | Done | BE-006; includes token issuance/refresh/logout as approved by contracts |
| BE-018 | Finalize forgot-password OTP delivery and validation workflow | QUANG | Done | Approved: Gmail SMTP, 6-digit OTP, Redis verifier/hash with 5-minute TTL and max 5 failed attempts per issued OTP; coordinate existing Redis primitives with BINH-DEV and confirm endpoint DTOs |
| BE-019 | Implement forgot/reset password with Gmail SMTP delivery and Redis OTP verification | QUANG | Done | BE-006, BE-018, BE-030; Spring Boot Mail dependency/config, secure Gmail credentials, expiry/attempt enforcement and no account enumeration; ROMS-002–003; 100% tests pass |
| ROMS-004–006 | Change password, profile and authorization support APIs | QUANG | Done | BE-005, BE-006; change-password, self-profile get/update; full tests passed |
| BE-022 | Define Gemini assistant API contract and menu-context format | QUANG | Done | Defined AiRecommendationRequest/Response DTOs and menu:context:v1 JSON format; full unit & WebMvc tests passed |
| BE-023 | Implement Redis Gemini menu/recipe context caching and invalidation | QUANG | Done | Implemented MenuContextService, builds context from DB (categories, items, ingredients), caches in Redis with invalidateContext(); no availability cached; 100% tests passed |
| BE-024 | Implement Gemini recommendations with live recipe/stock sellability and ID validation | QUANG | Done | Implemented AiAssistantService & Controller (POST /api/ai/recommendations), strict ID validation, live recipe/stock sellability check from PostgreSQL, graceful fallback to featured items; 143/143 project tests passed |

## 3. Menu and Table/Session Features

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-007 | Implement categories, menu item CRUD, Cloudinary menu-image integration and tablet menu read APIs | Member 3 | In Review | BE-028; ROMS-019–023, ROMS-025–026; approved category read/write, OWNER menu create/update and image upload/removal implemented. GET menu list/detail includes category, ordered batch-loaded images and live sellability via BE-008; tablet visibility is filtered before pagination. No hard category/menu delete; status remains BE-008. Source/test compilation passed; 165 non-DB regression tests passed using explicit Surefire 3.2.5 (54 BE-007 tests). Total BE-007 checks: 56. Await peer review/merge before Done. |
| BE-008 | Implement manual menu status changes and recipe/stock-based sellability checks | BINH-DEV | Done | Implemented PATCH /api/menu-items/{id}/status and GET /api/menu-items/{id}/sellability (live PostgreSQL recipe/stock check); 21/21 tests passed |
| BE-009 | Implement tables and service session APIs, including open/close and history | QUANG | Done | ROMS-011–015, 017–018, 024, 036; full CRUD tables, realtime status, session open/close with order guards, after-commit STOMP SessionStatusChangedEvent |
| BE-012 | Implement table transfer and transfer-history operations | QUANG | Done | ROMS-016; transfer active session to available active table, audit history in table_transfer_history, after-commit STOMP TableTransferEvent |

## 4. Ordering and Kitchen — BINH-DEV

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-010 | Implement Order/OrderItem creation, queries, atomic recipe-stock deduction and PENDING-only cancellation/restoration | BINH-DEV | Done | Implemented POST /api/orders (atomic stock deduction, negative ORDER_CONSUMPTION movements, 409 if insufficient), GET /api/orders, GET /api/orders/{id}, POST /api/orders/{id}/cancel & POST /api/orders/{id}/items/{itemId}/cancel |
| BE-011 | Implement kitchen acceptance, cooking, ready/served item workflow and Order completion | BINH-DEV | Done | Implemented GET /api/kitchen/orders, GET /api/kitchen/orders/{id}, POST /api/kitchen/orders/{id}/accept and PATCH /api/kitchen/orders/{id}/items/{itemId}/status |
| BE-021 | Publish and authorize relevant Order/Kitchen STOMP updates after DB commit | BINH-DEV | Done | Implemented dedicated OrderEventPublisher with afterCommit transaction synchronization |
| BE-027 | Test transitions, stock deduction/restoration, insufficient stock and concurrent Order creation/cancellation | BINH-DEV | Done | Implemented OrderConcurrencyIntegrationTest plus unit edge cases in OrderServiceImplTest and KitchenServiceImplTest |

## 5. Inventory, Recipes and Purchases — Member 4

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-014 | Ingredient management, current/minimum stock, stock alerts, inventory checks/adjustments and movement history | Member 4 | Done | BE-028; ROMS-043–044, 048–049; implemented Ingredient CRUD, PESSIMISTIC_WRITE locking, active menu item recipe deactivation check, atomic InventoryCheck discrepancy adjustments, StockMovement append-only audit trail and filtering |
| BE-033 | Recipe/BOM management and ingredient-cost queries | Member 4 | Done | BE-007, BE-014; ROMS-051–054; `recipe_items` uses `ingredients.unit`; implemented Recipe get/batch-replace/item add/patch/delete with active ingredient check and Redis MenuContext cache invalidation |
| BE-015 | Purchase list/create/detail and correction processing | Member 4 | Done | BE-014; ROMS-057–059; implemented Purchase list, create, detail, and atomic item correction with delta stock compensation and pessimistic locking |

## 6. Expenses, Reports and Dashboard — Member 5

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-026 | Expense list/create/update/cancel APIs | Member 5 | Done | Implemented POST /api/expenses, GET /api/expenses, GET /api/expenses/{id}, PUT /api/expenses/{id}, and POST /api/expenses/{id}/cancel |
| BE-016 | Dashboard/reporting queries and exports | Member 5 | Done | Implemented GET /api/dashboard/overview, GET /api/dashboard/best-selling, GET /api/dashboard/trends, and GET /api/dashboard/export (CSV export with UTF-8 BOM, OWNER only) |

## 7. Integration and Team Verification

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-025 | Verify Gmail OTP delivery/TTL/attempt limits, cache invalidation, STOMP authorization/delivery and Gemini availability | QUANG | Todo | BE-019, BE-021, BE-024 |
| BE-017 | Run cross-feature integration tests, schema/API contract review and release readiness | BINH-DEV | Todo | BE-025, BE-027 plus feature-owner verification |

## 8. Real Restaurant Production Transition — All Owners

| ID | Task / Deliverable | Owner | Status | Dependencies / Notes |
|---|---|---|---|---|
| BE-034 | Database migration V4: restaurant_settings, tax_categories, menu_items.tax_category_id, order_items tax snapshot | BINH-DEV | Done | Added V4 SQL migration; created RestaurantSetting, TaxCategory entities, updated MenuItem and OrderItem; entity mappings and schema validated |
| BE-035 | Restaurant Settings & Tax Categories API | BINH-DEV / Member 5 | Done | Implemented GET/PUT /api/settings, GET/POST /api/tax-categories with concurrent find-or-create logic and rate >= 0 check; unit & WebMvc tests passed (22/22 tests passed) |
| BE-036 | Update Order Creation & Cancellation for Service Staff | BINH-DEV | Done | Enabled SERVICE_STAFF & OWNER to cancel PENDING orders/items with automatic stock restoration; captured tax_rate and is_vat_inclusive snapshots on order_items; verified isolation and security in OrderServiceImplTest & OrderControllerTest |
| BE-037 | KDS FIFO Queue & Batch Action Endpoints | BINH-DEV | Done | Implemented FIFO sorting (created_at ASC) with JOIN FETCH; implemented cook-all and ready-all batch endpoints; added PATCH /api/orders/{id}/items/{itemId}/complete for staff; published ready alert to /topic/service/ready-dishes; verified in KitchenServiceImplTest & KitchenControllerTest |
| BE-038 | Apache POI Setup & Excel Import/Export for Menu Items | Member 3 | Done | Configured poi-ooxml; implemented GET template, GET export, POST import .xlsx; updates duplicate dish names; auto-creates missing tax categories and categories; verified in MenuItemExcelServiceImplTest & ExcelEndpointsControllerTest |
| BE-039 | Excel Import/Export for Ingredients | Member 4 | Done | Implemented GET template, GET export, POST import .xlsx; updates duplicate ingredient names; initializes currentStock and minimumStock; verified in IngredientExcelServiceImplTest & ExcelEndpointsControllerTest |
| BE-040 | Anti-Deadlock, Concurrency & N+1 Query Hardening | BINH-DEV | Done | Implemented deterministic ascending ingredient ID locking; parent-order lock before item updates; JOIN FETCH on KDS FIFO queue and Excel exports; verified with pessimistic locks & concurrency integration suite |

## Update Protocol

- The named owner maintains their rows.
- Mark `Done` only after code review and relevant tests.
- Keep original `BE-xxx` IDs and feature-ticket references stable.
