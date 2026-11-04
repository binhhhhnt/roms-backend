# ROMS Backend — Implementation Plan

**Status:** Shared, phased roadmap for five developers; assignment is maintained in `TASKS.md`.  
**Approach:** One Spring Boot backend, Three-Layer Architecture (Package by Layer), Flyway-managed PostgreSQL.

## 1. Preparation

- Verify the current application boots and connects to PostgreSQL.
- Confirm `V1__init_schema.sql`, `V2__update_order_status.sql`, and `V3__seed_data.sql` are applied.
- Apply Flyway `V4__restaurant_settings_and_tax.sql` for real restaurant operations (VAT settings, tax categories, order items tax snapshot).
- Add `org.apache.poi:poi-ooxml` for Excel template download, export, and transactional import.
- Confirm role access: `SERVICE_STAFF` directly creates orders and cancels PENDING orders; `OWNER` has full administrative and cancellation authority; `TABLE_ACCOUNT` is retained for guest viewing/AI/future QR; `KITCHEN_STAFF` operates KDS.

## 2. Implementation Phases

| Phase | Scope | Prerequisite |
|---|---|---|
| P0 — Foundation | Project configuration, Flyway validation, exception handling, API conventions | Existing project |
| P1 — Access | Users and schema-defined roles, JWT authentication, password/refresh-token hashing, Redis password-reset OTP | P0; approved security and OTP contracts |
| P2 — Catalog & Tables | Menu/categories/ingredients, menu availability, tables/service sessions | P0; relevant permissions |
| P3 — Service Flow | Orders, order items, kitchen workflow, table transfer; WebSocket/STOMP notifications | P2; approved status rules and STOMP contracts |
| P4 — Stock & Purchasing | Ingredient inventory, stock adjustments/alerts, purchasing records, expenses | P2; database contracts |
| P5 — AI & Reporting | Gemini menu assistance; Redis menu-context cache and invalidation; reporting endpoints | P2 menu/ingredients; current availability API; approved Gemini contract |
| P6 — Integration & Hardening | Cross-feature tests, JWT/OTP security tests, realtime delivery, cache invalidation, API stabilization | Relevant features completed |
| **P7 — Production Transition (Current)** | **V4 Migration, Restaurant Settings & VAT, Tax Categories, Menu/Ingredient Excel Import/Export, Staff Direct Ordering, KDS Batch Actions (`cook-all`/`ready-all`), and Anti-Deadlock/N+1 Hardening** | P0–P6 completed |

### Phase 7 Detail Breakdown:
1. **P7.1 — Database Migration V4**:
   - `restaurant_settings` (`menu_price_includes_vat BOOLEAN NOT NULL DEFAULT TRUE`).
   - `tax_categories` (`id BIGSERIAL PRIMARY KEY`, `rate DECIMAL(5,2) NOT NULL UNIQUE CHECK (rate >= 0)`).
   - `menu_items.tax_category_id` (Foreign Key).
   - `order_items` snapshot columns (`tax_rate DECIMAL(5,2)`, `is_vat_inclusive BOOLEAN`).
2. **P7.2 — Settings & Tax API**:
   - `GET /api/settings`, `PUT /api/settings` (OWNER).
   - `GET /api/tax-categories`, `POST /api/tax-categories` (with concurrent find-or-create logic).
3. **P7.3 — Staff Direct Ordering & Cancellation**:
   - Allow `SERVICE_STAFF` and `OWNER` to create orders under active sessions.
   - Allow `SERVICE_STAFF` and `OWNER` to cancel `PENDING` orders and order items with automatic stock restoration.
4. **P7.4 — KDS Batch Actions & Serving Completion**:
   - `POST /api/kitchen/orders/{id}/cook-all` (PENDING $\rightarrow$ COOKING).
   - `POST /api/kitchen/orders/{id}/ready-all` (COOKING $\rightarrow$ READY).
   - `PATCH /api/orders/{id}/items/{itemId}/complete` (READY $\rightarrow$ COMPLETED by staff).
   - STOMP event to `/topic/service/ready-dishes` notifying service staff when dishes are ready.
5. **P7.5 — Excel Import / Export**:
   - Menu items: template download, export current catalog, import with duplicate updating and auto-creation of missing tax rates.
   - Ingredients: template download, export current stock, import with duplicate updating and initial stock movement tracking.
6. **P7.6 — Concurrency & Performance Hardening**:
   - Deterministic ascending lock ordering (`ingredient_id ASC`) to eliminate deadlocks.
   - Pessimistic locking to prevent race conditions.
   - `JOIN FETCH` / `@EntityGraph` to eliminate N+1 queries in KDS, catalog, and export queries.

## 3. Team Workflow

1. Each member selects only assigned task IDs in `TASKS.md`.
2. Read `PRD.md`, `TECH_ARCHITECTURE.md`, the relevant Flyway migrations, and applicable API rules.
3. Check existing code and cross-feature dependencies; agree on any shared contracts before coding.
4. Implement Controller → Service interface/implementation → Repository with DTOs/mappers.
5. Run relevant checks, review the diff, and prepare a PR; do not alter other owners' work without agreement.
6. Update task status with evidence of checks and blockers.

## 4. Completion Gate

A task can be marked `Done` only after implementation matches approved requirements, relevant tests/checks pass (or limitations are documented), no unrelated changes are included, and the PR review/merge policy is satisfied.
