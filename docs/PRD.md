# ROMS Backend — Product Requirements

**Scope:** Single-restaurant backend | **Team:** 5 developers  
**Source of truth:** Approved use cases for workflows; applied Flyway migrations for database structure. Raise conflicts before coding.

## 1. Purpose

ROMS (Restaurant Operations Management System) supports dining-table service, menu and kitchen operations, ordering, inventory, purchasing, expenses, reporting, and menu-based AI assistance. The React frontend is in a separate project.

## 2. Actors and Access

The current V1 database supports these `users.role` values:

| Role | Main responsibility |
|---|---|
| `OWNER` | Restaurant administration, staff accounts, operational oversight |
| `SERVICE_STAFF` | Tables, sessions, order handling, serving dishes |
| `KITCHEN_STAFF` | Accept orders, prepare dishes, manage rapid menu availability |
| `INVENTORY_STAFF` | Ingredients, inventory checking, purchases |
| `TABLE_ACCOUNT` | Table-bound tablet account, session and ordering workflows |

Authorization must be enforced by the backend. The current V1 schema has **no** separate roles/permissions tables; do not generate them without a new approved migration.

### 2.1 Authentication Session Policy

| Account type | Access JWT | Refresh token | Concurrent login sessions |
|---|---|---|---|
| `TABLE_ACCOUNT` | 15 minutes | 90 days | **One** active session per account |
| `OWNER`, `SERVICE_STAFF`, `KITCHEN_STAFF`, `INVENTORY_STAFF` | 15 minutes | 7 days | **Multiple** sessions allowed |

- Every account can continue a valid session through automatic refresh-token rotation without re-entering credentials after a normal page reload.
- A new `TABLE_ACCOUNT` login revokes any previous login session; requests from a replaced session must be rejected even if an earlier access JWT has not yet expired.
- Staff and Owner may use multiple independently revocable sessions. The public API provides logout for the current session only; password change/reset and account deactivation revoke all sessions internally.
- A `TABLE_ACCOUNT` belongs to the table identified by `users.table_id`, not to an individual customer. Backend authorization must scope it to that assigned table.
- Disabled accounts and revoked/expired sessions must not retain access. Password hashes and refresh-token hashes must not be exposed.
- Refresh tokens have sliding expiry (90 days for tablets, 7 days for staff/Owner). No separate absolute session lifetime limit is imposed. Authentication endpoint contracts are defined in `API_CONVENTIONS.md`.


## 3. Functional Scope

| Domain | Required behavior |
|---|---|
| Authentication | Sign-in, JWT, password hashing, hashed refresh tokens and rotation, account-specific session policies, logout, and forgot-password email OTP (6 digits, 5-minute expiry, maximum 5 failed verification attempts per issued OTP) |
| User management | Manage users and approved role-based access |
| Menu | Categories, dishes, Cloudinary-hosted menu images, recipes (dish–ingredient associations) |
| Menu availability | Kitchen can manually mark dishes temporarily unavailable; ingredient-based out-of-stock is derived from current stock and recipes |
| Table & session | Table administration, start/close service sessions, staff-assisted opening, table transfers and history |
| Ordering | Customer/staff create orders, manage items, apply validated status transitions and cancellations |
| Kitchen | Accept orders, prepare items, signal ready items to service staff |
| Inventory | Ingredient management (current/minimum stock), inventory checks/adjustments, and per-ingredient stock movement history |
| Purchasing | View purchases, record already purchased/received goods, view purchase details, and correct incorrect purchase records while maintaining inventory consistency |
| Expenses | Record and cancel operational expenses |
| Reporting | Authorized operational summaries and analytics |
| Realtime | WebSocket/STOMP notifications for relevant operational changes |
| AI assistant | Gemini API suggestions using cached menu/ingredient context plus current dish availability |

**Out of scope:** Reservations, supplier management, payment processing, multi-branch operations, and unapproved features.

## 4. Status Rules

| Subject | Database values / derivation | Business meaning |
|---|---|---|
| Table operational state | `tables.is_active` | Active / out of service |
| Table realtime state | Derived | Out of service if inactive; occupied if an ACTIVE session exists; otherwise empty |
| Service session | `ACTIVE`, `CLOSED` | Serving session open / closed |
| Menu item | `AVAILABLE`, `TEMPORARILY_UNAVAILABLE`, `DISCONTINUED` | On sale / temporarily unavailable / discontinued |
| Ingredient operational state | `ingredients.is_active` | In use / inactive |
| Stock state | Derived from stock and configured minimum | Normal / low / out of stock |
| Expense | Derived from `expenses.cancelled_at` | Active / cancelled |

### 4.1 Order State Machine

`PENDING → COOKING → COMPLETED`  
`PENDING → CANCELLED`

- `PENDING`: submitted by customer or staff; not yet accepted by kitchen.
- `COOKING`: kitchen accepted the order; individual items may be `PENDING`, `COOKING`, `READY`, or `COMPLETED`.
- `COMPLETED`: all **non-cancelled** order items have reached `COMPLETED`.
- `CANCELLED`: cancellation of the entire order, allowed **only by `TABLE_ACCOUNT` while the order is `PENDING`** (for its own table).
- There is **no** `READY` or `REJECTED` status for the overall order.
- Do not mark an order complete when no active (non-cancelled) items remain; this exceptional case requires explicit handling.

### 4.2 Order Item State Machine

`PENDING → COOKING → READY → COMPLETED`  
`PENDING → CANCELLED`

- `PENDING`: awaiting kitchen processing.
- `COOKING`: being prepared.
- `READY`: kitchen finished; awaiting delivery to table.
- `COMPLETED`: served to customer.
- An individual item may be cancelled **only by `TABLE_ACCOUNT` while that item is `PENDING`** (for its own table).
- Cancelled items do not count toward the parent's completion condition.
- Cancelling an entire PENDING order cancels its remaining PENDING items atomically.
- Item status changes and overall order completion must be evaluated transactionally in the service layer.
- Order `COOKING` can contain already `COMPLETED` items; it stays `COOKING` until all remaining non-cancelled items complete.
- Order items and quantities cannot be modified after order creation.
- Additional dishes require a new Order under the same active Service Session.
- Cancelled OrderItems remain in the database for audit purposes but are hidden from normal active-order views.

**Schema alignment:** V1 currently permits `REJECTED` and `READY` in `orders`, and does not permit `COMPLETED` in `order_items`. An approved Flyway migration must reconcile those CHECK constraints before implementing these transitions.

## 5. Core Data and Operational Rules

- One table can have at most one `ACTIVE` service session (enforced by the partial unique index in V1).
- Order/table association follows `orders.service_session_id`; orders do not store table ID directly.
- Order item `unit_price` is a snapshot at ordering time.
- There is no `menu_items.available_quantity` or serving-quota/override workflow. A dish can be ordered only if its menu status permits sale and every ingredient required by its recipe is sufficient for the requested quantity. Insufficient ingredients make the dish effectively out of stock without rewriting `menu_items.status`.
- On successful Order creation, aggregate recipe requirements across all OrderItems, validate and deduct ingredient stock, persist Order/OrderItems and append negative `ORDER_CONSUMPTION` stock movements in one transaction. Insufficient stock rejects the entire Order with `409 Conflict`; no partial creation and no negative ingredient balances.
- Kitchen acceptance (`PENDING → COOKING`) changes workflow state only; it must not deduct stock again.
- Cancelling a `PENDING` Order restores the deducted ingredients for its still-active PENDING OrderItems; cancelling an individual `PENDING` OrderItem restores its deduction. Restore from the original recorded movements, not the current recipe; append compensating positive `ORDER_CONSUMPTION` movements, never alter history. Ensure cancellation cannot restore stock twice.
- The current stock is in `ingredients.current_stock` (never negative); changes must be recorded consistently in the append-only `stock_movements` ledger. Order deductions and cancellation compensations reference the relevant `order_item_id`.
- Inventory workflow: Ingredient Management → Purchase List (Create Purchase / Purchase Detail) → Inventory Check / Adjustment → Stock Movement History.
- Purchases record goods already purchased and received, not pending purchase orders.
- Authorized staff may correct incorrect purchase records. Corrections affecting stock must update inventory balances and append traceable stock movements atomically.
- Historical stock movements must not be edited or deleted.
- Forgot-password OTP is delivered through Gmail SMTP: a securely generated 6-digit code, valid for 5 minutes, with at most 5 failed verification attempts per issued OTP. Store only an OTP verifier/hash and retry/expiry state in Redis; invalidate the OTP on successful use or when attempts are exhausted. Do not reveal whether an email/account exists in public recovery responses.
- Menu images are stored in Cloudinary; PostgreSQL `menu_item_images` stores image references/URLs and existing approved metadata, not binary image contents. Upload and deletion must be authorized and validated; do not invent database columns without an approved migration.
- Redis stores expiring password-reset OTP verification data and relatively stable menu/ingredient context for Gemini.
- Invalidate menu-context cache after committed menu, recipe, category or ingredient description changes; derive live sellability from menu status, recipe and current ingredient stock in PostgreSQL for recommendations and order placement.
- Realtime notifications do not replace REST operations or the persistent PostgreSQL state.

## 6. Development Acceptance Criteria

For a backend task to be done:
- It follows the agreed use cases, 3-layer architecture and API conventions.
- Permissions, validation and status rules are checked by service logic.
- Entity mappings reflect the **entire applied Flyway migration history**, not only V1.
- State-changing operations use appropriate transactions; tests cover success, failure, and invalid transitions.
- `TASKS.md` is updated with verified progress.

**Decisions resolved:** Order and OrderItem cancellation is restricted exclusively to `TABLE_ACCOUNT` for its own table while the order/item is `PENDING`. Staff and Kitchen cannot cancel orders.
