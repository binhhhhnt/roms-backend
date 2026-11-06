# ROMS Backend — Product Requirements

**Scope:** Single-restaurant backend | **Team:** 5 developers  
**Source of truth:** Approved use cases for workflows; applied Flyway migrations for database structure. Raise conflicts before coding.

## 1. Purpose

ROMS (Restaurant Operations Management System) supports dining-table service, staff-assisted direct ordering, menu and kitchen operations (KDS), inventory, purchasing, expenses, reporting, tax & restaurant settings, Excel batch import/export, and menu-based AI assistance. The React frontend is in a separate project.

## 2. Actors and Access

The current database supports these `users.role` values:

| Role | Main responsibility |
|---|---|
| `OWNER` | Restaurant administration, staff accounts, operational oversight, order cancellation while PENDING, settings & taxes |
| `SERVICE_STAFF` | Tables, sessions, direct table ordering, serving dishes, order and item cancellation while PENDING |
| `KITCHEN_STAFF` | FIFO order queue, cook/ready dishes (individual and batch "cook-all" / "ready-all"), rapid menu availability |
| `INVENTORY_STAFF` | Ingredients, inventory checking, purchases, Excel import/export |
| `TABLE_ACCOUNT` | Table-bound tablet account for guest menu browsing, order tracking, and AI assistance (precursor for QR ordering) |

Authorization must be enforced by the backend. The current schema has **no** separate roles/permissions tables; do not generate them without a new approved migration.

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
| Restaurant Settings & VAT | Singleton `restaurant_settings` (`menu_price_includes_vat`), `tax_categories` (`rate >= 0` UNIQUE), menu item tax classification, and snapshot VAT calculation on order items |
| Menu & Availability | Categories, dishes, Cloudinary menu images, recipes (dish–ingredient associations), Excel template/export/import with auto-creation of missing tax categories; Kitchen toggles `AVAILABLE` ↔ `TEMPORARILY_UNAVAILABLE` |
| Table & session | Table administration, start/close service sessions, staff opening, table transfers and history |
| Ordering | Staff creates orders directly at table, manages items, applies validated status transitions; cancellation allowed by `SERVICE_STAFF` and `OWNER` while PENDING |
| Kitchen (KDS) | FIFO queue; individual "Nấu" (COOKING) / "Ra món" (READY) and batch "Nấu tất cả" (`cook-all`) / "Ra tất cả" (`ready-all`); emits realtime STOMP notification to service staff when dishes are READY |
| Serving | Service staff delivers ready dishes to table and marks "Hoàn thành" (`COMPLETED`) |
| Inventory | Ingredient management (current/minimum stock), inventory checks/adjustments, per-ingredient stock movement history, and Excel template/export/import |
| Purchasing | View purchases, record already purchased/received goods, view purchase details, and correct incorrect purchase records while maintaining inventory consistency |
| Expenses | Record and cancel operational expenses |
| Reporting | Authorized operational summaries, VAT-aware sales metrics, and CSV analytics |
| Realtime | WebSocket/STOMP notifications for kitchen orders, dish readiness, and session status |
| AI assistant | Gemini API suggestions using cached menu/ingredient context plus current dish availability |

**Out of scope:** Reservations, supplier management, payment gateway integration, multi-branch operations, and unapproved features.

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

- `PENDING`: submitted by staff (or table account); awaiting kitchen processing.
- `COOKING`: kitchen accepted/cooking order; individual items may be `PENDING`, `COOKING`, `READY`, or `COMPLETED`.
- `COMPLETED`: all **non-cancelled** order items have reached `COMPLETED`.
- `CANCELLED`: cancellation of the entire order, allowed by **`SERVICE_STAFF`** and **`OWNER`** (and table account for its own table) **only while the order is `PENDING`**.
- There is **no** `READY` or `REJECTED` status for the overall order.
- Do not mark an order complete when no active (non-cancelled) items remain; this exceptional case requires explicit handling.

### 4.2 Order Item State Machine

`PENDING → COOKING → READY → COMPLETED`  
`PENDING → CANCELLED`

- `PENDING`: awaiting kitchen preparation.
- `COOKING`: being prepared by kitchen ("Nấu" or "Nấu tất cả").
- `READY`: kitchen finished preparation; at service counter awaiting delivery to table ("Ra món" or "Ra tất cả").
- `COMPLETED`: served to customer by service staff.
- An individual item may be cancelled by **`SERVICE_STAFF`** and **`OWNER`** **only while that item is `PENDING`**.
- Cancelled items do not count toward the parent's completion condition.
- Cancelling an entire PENDING order cancels its remaining PENDING items atomically.
- Item status changes and overall order completion must be evaluated transactionally in the service layer.
- Order `COOKING` can contain already `COMPLETED` items; it stays `COOKING` until all remaining non-cancelled items complete.
- Order items and quantities cannot be modified after order creation. Additional dishes require a new Order under the same active Service Session.
- Cancelled OrderItems remain in the database for audit purposes but are hidden from normal active-order views.

## 5. Core Data and Operational Rules

- One table can have at most one `ACTIVE` service session (enforced by partial unique index).
- Order/table association follows `orders.service_session_id`; orders do not store table ID directly.
- **VAT & Tax Engine**:
  - `restaurant_settings.menu_price_includes_vat` determines whether listed menu prices already include VAT.
  - `tax_categories` holds `rate DECIMAL(5,2) NOT NULL UNIQUE CHECK (rate >= 0)`.
  - When creating order items, record snapshot `unit_price`, `tax_rate`, and `is_vat_inclusive` to ensure historical billing and tax integrity.
  - If `menu_price_includes_vat = true`: $\text{PriceBeforeTax} = \frac{\text{UnitPrice}}{1 + \text{rate}/100}$, $\text{TaxAmount} = \text{UnitPrice} - \text{PriceBeforeTax}$, $\text{CustomerPays} = \text{UnitPrice}$.
  - If `menu_price_includes_vat = false`: $\text{PriceBeforeTax} = \text{UnitPrice}$, $\text{TaxAmount} = \text{UnitPrice} \times \frac{\text{rate}}{100}$, $\text{CustomerPays} = \text{UnitPrice} + \text{TaxAmount}$.
- **Menu Availability & Inventory Deduction**:
  - A dish can be ordered only if its status is `AVAILABLE` and every ingredient in its recipe is sufficient.
  - On Order creation: Aggregate recipe requirements across all requested items, lock ingredient rows in ascending ID order (`ingredient_id ASC`), check stock and deduct atomically, append negative `ORDER_CONSUMPTION` movements. Insufficient stock rejects the order with `409 Conflict`.
  - Kitchen transition to `COOKING` (single or batch) changes workflow state only; **does not deduct stock again**.
  - Cancelling a `PENDING` Order/OrderItem restores deducted stock atomically based on original stock movements, appending positive compensating movements.
- **Kitchen Display System (KDS)**:
  - Orders are queued in FIFO order (`created_at ASC`).
  - Kitchen staff can trigger "Nấu" (item) or "Nấu tất cả" (order) to move items from `PENDING` to `COOKING`.
  - Kitchen staff can trigger "Ra món" (item) or "Ra tất cả" (order) to move items from `COOKING` to `READY`.
  - On dish reaching `READY`, the system emits a STOMP notification to service staff. Service staff delivers dish and calls complete endpoint (`COMPLETED`).
- **Excel Import / Export**:
  - Menu Items: Download template (`.xlsx`) with category dropdown validation or export existing catalog. Import parses rows; if category does not exist, reject row with validation error. If item name already exists, update its fields.
  - Ingredients: Download template or export existing list. Import parses rows; if an ingredient name already exists, update its properties; if initial stock is provided, record an initial balance movement.
- **Deadlock, Race Condition & N+1 Prevention**:
  - Always lock ingredients in deterministic ascending ID order (`ORDER BY id ASC`).
  - Always lock parent `orders` row before mutating child `order_items`.
  - Use `JOIN FETCH` / `@EntityGraph` for KDS order feeds, menu catalog, recipe aggregation, and Excel exports to completely eliminate N+1 queries.
- Cloudinary holds menu images; PostgreSQL stores URLs/references.
- Redis caches menu/ingredient context for Gemini AI and expiring forgot-password OTPs.
- Owner-approved catalog deletion: an empty category may be deleted; a category containing dishes returns a conflict. A dish may be deleted with its recipe/images only if it has never been referenced by an OrderItem. Preserve all order and stock history; referenced dishes may instead be discontinued. Category UI status labels are Đang phục vụ / Tạm ngưng phục vụ, backed by `isActive`.

## 6. Development Acceptance Criteria

For a backend task to be done:
- It follows the agreed use cases, 3-layer architecture, and API conventions.
- Permissions, validation, and status rules are checked by service logic.
- Entity mappings reflect the entire applied Flyway migration history including `V4`.
- Concurrency control prevents deadlocks and double deductions/restorations.
- No N+1 queries in KDS, catalog, or export paths.
- `TASKS.md` is updated with verified progress.
