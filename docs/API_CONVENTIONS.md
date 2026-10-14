# ROMS Backend — API Conventions

**Status:** Approved response, pagination, and authentication-session policies; remaining implementation contracts are marked.  
**Scope:** Spring Boot REST APIs consumed by the separate React project.

## 1. Endpoints

- Prefix endpoints with `/api` (confirm the final prefix with the frontend before publishing contracts).
- Use plural resource nouns, e.g. `/api/orders`, `/api/menu-items`.
- Use `GET` for reads, `POST` for creation, `PUT` for full replacement, `PATCH` for partial/state changes, and `DELETE` only where deletion is an approved business operation.
- Use path IDs for one resource (`/api/orders/{id}`), query parameters for filters (`?status=...`).
- Do not invent endpoints or actions without an approved use case.

## 2. HTTP Status

| Status | Use |
|---|---|
| 200 | Successful read/update |
| 201 | Resource created |
| 204 | Successful operation with no response body |
| 400 | Malformed request or invalid request parameters |
| 401 | Not authenticated |
| 403 | Authenticated but not authorized |
| 404 | Resource not found |
| 409 | Conflict with current resource/business state |
| 500 | Unhandled server error (no internal details exposed) |

Use 400 for request validation errors until the team explicitly approves a different standard. Use 409 for valid workflow actions conflicting with the current state.

## 3. Request and Response

- JSON uses `camelCase` properties; Java DTOs use corresponding camelCase fields.
- Use dedicated `*Request` and `*Response` DTOs; never serialize JPA entities directly.
- Validate DTOs with Jakarta Bean Validation; validate business conditions in services.
- Dates/times use ISO 8601. Agree on UTC vs local restaurant time before implementing time-sensitive workflows.
- Amounts/prices use decimal-safe types (e.g. Java `BigDecimal`), never floating-point for money.
- Keep optional/null fields consistent across endpoints.

## 4. Standard API Response

All APIs returning JSON responses must use `ApiResponse<T>`.

### Success Response

```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 101,
    "status": "PENDING"
  }
}
```

### Error Response

```json
{
  "success": false,
  "message": "Order cannot be cancelled in its current state",
  "error": {
    "code": "INVALID_ORDER_STATUS",
    "details": null
  }
}
```

### Pagination Response

```json
{
  "success": true,
  "message": "Orders retrieved successfully",
  "data": {
    "content": [],
    "page": 0,
    "size": 10,
    "totalElements": 100,
    "totalPages": 10
  }
}
```

### Rules

- Use `ApiResponse<T>` for all JSON response bodies.
- Use `PageResponse<T>` inside `data` for paginated results.
- Success responses use `success`, `message`, and `data`.
- Error responses use `success`, `message`, and `error`.
- Validation errors are included in `error.details`.
- Use consistent error codes and messages through `GlobalExceptionHandler`.
- Return appropriate HTTP status codes; do not return HTTP 200 for errors.
- HTTP `204 No Content` must have an empty response body.
- Never expose internal stack traces or JPA entities.

## 5. Filtering, Pagination, Sorting

- Use query parameters for pagination and filtering on list endpoints.
- Use `page` (0-based), `size`, and `sort` query parameters for paginated endpoints.
- Enforce a sensible server-side maximum page size and stable ordering.
- Return paginated results using `PageResponse<T>` inside `ApiResponse<T>.data`.

## 6. Workflow State APIs and Conflicts

- Use approved request DTOs and task-specific endpoints to trigger workflow actions; **do not allow arbitrary client-provided status updates**.
- Follow the authoritative state machines in `PRD.md`: Order (`PENDING`, `COOKING`, `COMPLETED`, `CANCELLED`) and OrderItem (`PENDING`, `COOKING`, `READY`, `COMPLETED`, `CANCELLED`). Overall Order has no `READY` or `REJECTED`.
- Cancel an Order or OrderItem only when the respective target is `PENDING`; return **409 Conflict** if its current state forbids cancellation or transition.
- For an Order in `COOKING`, item completion does not imply parent completion unless **all non-cancelled items** have completed. Do not auto-complete when zero active items remain.
- Return **400 Bad Request** for malformed status/action input; **403 Forbidden** for insufficient permissions; **404 Not Found** for missing resources; **409 Conflict** for a valid action prohibited by current state.
- State changes are atomic at the service layer. The response reflects the persisted result; STOMP messages are secondary notifications after commit.
- Do not introduce Reservation endpoints; the feature is out of scope.
- Order creation includes its initial OrderItems; do not expose APIs for adding items to existing Orders or changing OrderItem quantities.
- Additional dishes must be submitted through a new Order within the active Service Session.
- Normal active-order responses exclude cancelled OrderItems; historical/audit views may include them.
- Order creation must verify current menu status and available ingredients for **all** requested quantities using recipes, and atomically deduct stock and persist negative `ORDER_CONSUMPTION` movements with the Order. If insufficient, return `409 Conflict` with the standard error envelope; create nothing.
- Kitchen acceptance (`PENDING → COOKING`) must not deduct ingredients again. Cancelling a permitted PENDING Order/OrderItem must atomically restore the amount actually deducted and append compensating movements; reject invalid or duplicate state transitions with `409`.
- Ingredient-based out-of-stock is derived from recipe and live stock. There is no `available_quantity`, serving-quota override or API to change that field; manual `menu_items.status` remains distinct from stock availability.

## 7. Authentication & Authorization

- Use Spring Security + **JJWT/HS256**, with **15-minute** bearer access JWTs and opaque refresh tokens held in `HttpOnly` cookies. JWT claims are `sub` (user ID), `role`, `sid` (session ID), `iat`, `exp`.
- **Refresh expiration:** `TABLE_ACCOUNT` 90 days; staff/Owner 7 days. Rotate on **every** successful refresh; extend the expiration each time (sliding expiration, without an additional absolute lifetime limit).
- `TABLE_ACCOUNT` has **one active session**. New login revokes its previous session immediately. Staff/Owner may have **multiple sessions**; logout revokes only the current one. Password change/reset and user deactivation revoke all of that account's sessions internally; there is **no public logout-all API**.
- Store refresh-token SHA-256 hashes in PostgreSQL. Session identity remains stable across rotation (`sid` = `refresh_tokens.id`); reject replay, expired tokens and revoked sessions. Verify `sid` and account status in PostgreSQL on authenticated API requests so revoked access tokens stop working promptly.
- Derive a `TABLE_ACCOUNT`'s permitted table from `users.table_id`, never a client-provided table ID. Enforce all role permissions server-side.

### 7.1 Auth Endpoints

| Method | Endpoint | Request | Success |
|---|---|---|---|
| `POST` | `/api/auth/login` | `{ "username": "...", "password": "..." }` | `200` + access token JSON, sets refresh cookie |
| `POST` | `/api/auth/refresh` | Refresh cookie (no JSON body) | `200` + new access token JSON, rotates refresh cookie |
| `POST` | `/api/auth/logout` | `Authorization: Bearer <accessToken>` | `204 No Content`, revokes current session and clears cookie |
| `GET` | `/api/auth/me` | `Authorization: Bearer <accessToken>` | `200` + authenticated user profile JSON |

**Login/refresh JSON data:** `{ "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900 }` (seconds); never include the refresh token. **Me JSON data:** `{ "id": 12, "username": "...", "fullName": "...", "role": "TABLE_ACCOUNT", "tableId": 1 }` (`tableId` is nullable for staff). Wrap these objects in the approved `ApiResponse<T>` success envelope; use Section 4 error format and correct HTTP statuses. Never reveal whether a username exists in an authentication failure.

### 7.2 Browser Cookie and Request Rules

- Access JWTs are kept in React **memory** and sent in `Authorization: Bearer`; on reload, React calls `/api/auth/refresh` to rehydrate the login.
- Refresh cookie: `HttpOnly`, `Secure` on HTTPS, `Path=/api/auth`, `SameSite=Lax` when FE/BE are same-site. Set cookie Max-Age according to account role; clear it using matching cookie attributes on logout. For local non-HTTPS development, configure only a dev-profile exception to `Secure`.
- Configure CORS with explicit allowed frontend origins and credentials; never use wildcard origins with credentialed requests. React uses `credentials: 'include'` for login/refresh/logout as appropriate.
- Validate `Origin` on cookie-auth requests and use `SameSite` protection. If production FE and BE are **cross-site**, configure `SameSite=None; Secure` and an explicit CSRF token mechanism before deploying; CORS alone is insufficient.
- Authenticate logout with a valid Bearer token, not only the cookie. Cookie-less protected endpoints use Bearer JWT authentication. Invalid/expired/revoked authentication returns `401`; insufficient permission returns `403`.

## 8. Menu Image Upload and Password-Recovery Contracts

- Menu-image uploads/removals are authorized backend operations using **Cloudinary**. Use a validated multipart upload request where appropriate; validate allowed image type and file size. Store only Cloudinary URL/reference information in existing `menu_item_images` columns; do not expose provider secrets or assume extra columns exist.
- Menu-image responses use the normal `ApiResponse<T>` JSON envelope. File-upload and removal endpoint paths, DTO fields, size limits, and Cloudinary deletion mapping must be confirmed by the owning developer before publication; do not invent endpoint paths.
- Forgot-password OTP is sent through **Gmail SMTP** to the account email. OTP has **6 digits**, **5-minute TTL**, and **at most 5 failed verification attempts per issued OTP**. Backend stores an OTP verifier/hash and attempts in Redis; never return OTP or verifier in JSON.
- Public forgot-password responses must not disclose whether the account/email exists. OTP verification/reset must enforce expiry, attempt limit and single use; use the standard error envelope for invalid, expired or exhausted codes without leaking secrets.
- Exact forgot/reset password endpoint paths and request/response DTOs require confirmation with QUANG; these rules do not change the four existing login/refresh/logout/me endpoints.

## 9. Contract Ownership

- Every published API must have a documented request DTO, response DTO, status codes, and authorization requirement.
- Owners of related features coordinate endpoint and DTO changes before merge.
- When API decisions are still pending, mark the task blocked; do not let individual AI agents silently choose incompatible response formats.
