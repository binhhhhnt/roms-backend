# ROMS Backend — Implementation Plan

**Status:** Shared, phased roadmap for five developers; assignment is maintained in `TASKS.md`.  
**Approach:** One Spring Boot backend, Three-Layer Architecture (Package by Layer), Flyway-managed PostgreSQL.

## 1. Preparation

- Verify the current application boots and connects to PostgreSQL.
- Confirm `V1__init_schema.sql` is consistent with the actual database and existing entities. Apply an approved V2 migration reconciling `orders` and `order_items` status constraints; do not edit an already applied/shared V1.
- Transition Hibernate schema handling from `update` to `validate` once migration parity is verified.
- Agree on shared Git branch/PR conventions, JWT/refresh-token and OTP contracts, API response/error format, and migration version coordination.
- Confirm the five roles present in V1 (`OWNER`, `SERVICE_STAFF`, `KITCHEN_STAFF`, `INVENTORY_STAFF`, `TABLE_ACCOUNT`); V1 has no separate roles/permissions tables.
- Approve dependencies and contracts for WebSocket/STOMP, Redis, and Gemini before implementing integrations.

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

Order lifecycle: overall `PENDING → COOKING → COMPLETED` or `PENDING → CANCELLED`; per item `PENDING → COOKING → READY → COMPLETED` or `PENDING → CANCELLED`. An order stays `COOKING` until all non-cancelled items are `COMPLETED`. Cancel only while the target order/item is `PENDING`; handle orders with no active items explicitly.

Redis has **two approved uses only**: password-reset OTP (TTL and limited attempts) and relatively stable Gemini menu context (items/categories/descriptions/ingredients). Availability is read live from PostgreSQL and validated before recommendations and orders; menu-context cache is invalidated after committed menu/ingredient edits. No Redis rate limiting or WebSocket broker.

WebSocket/STOMP delivers notifications after successful transaction commit; REST remains authoritative. Gemini cannot change orders or database records.

Phases describe **dependencies, not strict serial execution**. Independent, well-defined tasks may run in parallel after their shared contracts are agreed.

## 3. Team Workflow

1. Each member selects only assigned task IDs in `TASKS.md`.
2. Read `PRD.md`, `TECH_ARCHITECTURE.md`, the relevant Flyway migrations, and applicable API rules.
3. Check existing code and cross-feature dependencies; agree on any shared contracts before coding.
4. Implement Controller → Service interface/implementation → Repository with DTOs/mappers.
5. Run relevant checks, review the diff, and prepare a PR; do not alter other owners' work without agreement.
6. Update task status with evidence of checks and blockers.

## 4. Shared Change Policy

- Business requirement changes require team approval and PRD updates.
- Architecture/dependency changes require approval and architecture updates.
- Applied/shared Flyway migrations are immutable; coordinate the next migration version before merge.
- API contract changes require coordination with affected backend owners and the separate frontend team.

## 5. Completion Gate

A task can be marked `Done` only after implementation matches approved requirements, relevant tests/checks pass (or limitations are documented), no unrelated changes are included, and the PR review/merge policy is satisfied.
