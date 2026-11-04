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
| Excel import/export | Apache POI (`org.apache.poi:poi-ooxml`) |
| Existing helpers | Jakarta Validation, Lombok, Actuator, Spring Boot test starters |

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
- Security, STOMP, Redis, Cloudinary, Excel processing and Gemini are infrastructure/integration concerns; business workflow still enters the service layer.
- Return DTOs to clients, never expose JPA entities directly or rely on frontend validation alone.

## 3. Package Structure and Naming

```text
com.roms/
├── controller/             OrderController, KitchenController, SettingController, TaxCategoryController
├── service/                OrderService, KitchenService, SettingService, ExcelService (interfaces)
│   └── impl/               OrderServiceImpl, KitchenServiceImpl, SettingServiceImpl, ExcelServiceImpl
├── repository/             OrderRepository, OrderItemRepository, SettingRepository, TaxCategoryRepository
├── entity/                 Order, OrderItem, RestaurantSetting, TaxCategory, MenuItem, Ingredient
├── dto/
│   ├── request/            CreateOrderRequest, UpdateSettingRequest, ImportResultResponse
│   └── response/           OrderResponse, KitchenOrderResponse, SettingResponse, TaxCategoryResponse
├── mapper/                 OrderMapper, SettingMapper, MenuItemMapper
├── enums/                  OrderStatus, OrderItemStatus, Role
├── security/               authentication/JWT components
├── websocket/              STOMP config, StompEventPublisher, notification adapters
├── integration/            Gemini client adapter, Cloudinary adapter, Excel parser/builder
├── config/                 framework configuration (Security, Web, WebSocket, Redis, Mail)
├── exception/              domain exceptions, GlobalExceptionHandler
└── common/                 truly shared utilities/types only
```

## 4. Controller and DTO Rules

- Prefer thin `@RestController` classes with constructor-injected service interfaces.
- Receive validated request DTOs (`@Valid`); use path/query values only for identifiers, filters and pagination.
- Keep entity lookup, pricing, VAT computation, permission-sensitive decisions and state transitions in services.
- Never accept client-supplied authoritative values such as `createdBy`, server timestamps, derived totals, or trusted statuses without server checks.
- Return dedicated response DTOs and agreed HTTP status codes; consult `API_CONVENTIONS.md` for response envelopes, errors and pagination.

## 5. Service Pattern, Transactions and Concurrency Architecture

### 5.1. Deadlock Prevention Strategy
- **Deterministic Lock Ordering**:
  - When creating orders (deducting stock) or cancelling orders (restoring stock): always sort ingredient IDs in ascending order (`ingredient_id ASC`) before acquiring `PESSIMISTIC_WRITE` locks (`SELECT ... FOR UPDATE`).
  - This guarantees all concurrent threads acquire row locks in the exact same order ($1 \rightarrow 2 \rightarrow 3$), eliminating circular wait deadlocks.
- **Parent-Child Locking**:
  - In KDS batch actions (`cook-all`, `ready-all`) or item status transitions: always lock the parent `orders` record before updating child `order_items`.

### 5.2. Race Condition Prevention Strategy
- **Atomic Stock Deduction & Restoration**:
  - Check stock after acquiring pessimistic row locks. If any ingredient is insufficient, rollback immediately with `409 Conflict`.
  - Hủy đơn kiểm tra `status == 'PENDING'` dưới khóa bi quan; nếu trạng thái đã thay đổi (bếp đã nhận hoặc luồng khác đã hủy), từ chối ngay với `409 Conflict`.
  - Idempotent restoration: hoàn trả nguyên liệu đúng bằng số lượng đã ghi trong bản ghi `stock_movements` ban đầu, ngăn chặn việc hoàn kho 2 lần.
- **Tax Category Concurrency**:
  - Table `tax_categories` có `rate DECIMAL(5,2) NOT NULL UNIQUE CHECK (rate >= 0)`.
  - Khi Import Excel phát hiện tax rate mới: phương thức `findOrCreateTaxCategory` bắt `DataIntegrityViolationException` để lấy lại bản ghi nếu luồng khác vừa tạo đồng thời.

### 5.3. N+1 Query Elimination Strategy
- **KDS Queue & Orders**:
  - Sử dụng `JOIN FETCH` để gom `Order`, `OrderItem` và `MenuItem` trong 1 câu SQL duy nhất (`SELECT DISTINCT o FROM Order o JOIN FETCH o.orderItems oi JOIN FETCH oi.menuItem mi ...`), tránh N+1 khi load danh sách đơn bếp.
- **Menu Catalog**:
  - Sử dụng `@EntityGraph(attributePaths = {"category", "taxCategory"})` khi truy vấn danh sách món.
  - Sử dụng `@BatchSize(size = 50)` trên `menuItemImages`.
- **Recipe BOM Calculation**:
  - Gom toàn bộ `menu_item_id` trong giỏ hàng để query toàn bộ công thức bằng một truy vấn `WHERE r.menuItem.id IN (:ids)`.
- **Excel Export**:
  - Single-query fetch toàn bộ dữ liệu kèm quan hệ liên quan, không gọi repository bên trong vòng lặp ghi dòng Excel.

## 6. Restaurant Settings & Tax Engine

### 6.1. Database Schema
- **`restaurant_settings`**:
  - Singleton record (`id = 1`).
  - `menu_price_includes_vat BOOLEAN NOT NULL DEFAULT TRUE`.
- **`tax_categories`**:
  - `id BIGSERIAL PRIMARY KEY`.
  - `rate DECIMAL(5,2) NOT NULL UNIQUE CHECK (rate >= 0)`.
- **`menu_items`**:
  - `tax_category_id BIGINT REFERENCES tax_categories(id)`.
- **`order_items`**:
  - `unit_price NUMERIC(12,2) NOT NULL`.
  - `tax_rate DECIMAL(5,2) NOT NULL`.
  - `is_vat_inclusive BOOLEAN NOT NULL`.

### 6.2. VAT Calculation Rules
- If `menu_price_includes_vat = true`:
  $$\text{PriceBeforeTax} = \frac{\text{UnitPrice}}{1 + \text{rate}/100}, \quad \text{TaxAmount} = \text{UnitPrice} - \text{PriceBeforeTax}, \quad \text{Total} = \text{UnitPrice}$$
- If `menu_price_includes_vat = false`:
  $$\text{PriceBeforeTax} = \text{UnitPrice}, \quad \text{TaxAmount} = \text{UnitPrice} \times \frac{\text{rate}}{100}, \quad \text{Total} = \text{UnitPrice} + \text{TaxAmount}$$

## 7. Kitchen Display System (KDS) & Serving Flow

### 7.1. State Machine
- **Order:** `PENDING → COOKING → COMPLETED` / `PENDING → CANCELLED`
- **OrderItem:** `PENDING → COOKING → READY → COMPLETED` / `PENDING → CANCELLED`
- **Cancellation Authority:** `SERVICE_STAFF` and `OWNER` can cancel while target is `PENDING`.

### 7.2. KDS Endpoints
- `GET /api/kitchen/orders`: FIFO queue (`created_at ASC`), filters by status.
- `PATCH /api/kitchen/orders/{id}/items/{itemId}/status`: Update single item (`COOKING` or `READY`).
- `POST /api/kitchen/orders/{id}/cook-all`: Batch transition all PENDING items to `COOKING`.
- `POST /api/kitchen/orders/{id}/ready-all`: Batch transition all COOKING items to `READY`.
- `PATCH /api/orders/{id}/items/{itemId}/complete`: Staff serving confirmation (moves item to `COMPLETED`; auto-completes parent order when all active items are done).

### 7.3. STOMP Realtime Topics
- `/topic/kitchen/orders`: New orders placed (`PENDING`), order cancellations.
- `/topic/session/{sessionId}/orders`: Item and order status changes.
- `/topic/service/ready-dishes`: Realtime notification to service staff when dishes reach `READY` (includes table number, order ID, dish name, quantity, notes).

## 8. Excel Import / Export Architecture

- **Menu Items (`/api/menu-items/excel/*`)**:
  - `GET /api/menu-items/excel/template`: Download standard empty template.
  - `GET /api/menu-items/excel/export`: Export existing menu items with Category, Price, Tax Rate, Description.
  - `POST /api/menu-items/excel/import`: Upload `.xlsx`. Updates existing items if name matches; creates new items if absent; creates new `tax_categories` entry if rate does not exist.
- **Ingredients (`/api/ingredients/excel/*`)**:
  - `GET /api/ingredients/excel/template`: Download standard empty template.
  - `GET /api/ingredients/excel/export`: Export existing ingredients with Unit, Current Stock, Minimum Stock.
  - `POST /api/ingredients/excel/import`: Upload `.xlsx`. Updates existing ingredients if name matches; inserts new ones; appends `INITIAL_STOCK` movements for initial balances.

## 9. Database Migrations (Flyway)

- V1: `src/main/resources/db/migration/V1__init_schema.sql`
- V2: `src/main/resources/db/migration/V2__update_order_status.sql`
- V3: `src/main/resources/db/migration/V3__seed_data.sql`
- **V4 (Pending):** `src/main/resources/db/migration/V4__restaurant_settings_and_tax.sql`
  - Creates `restaurant_settings` with `menu_price_includes_vat`.
  - Creates `tax_categories` with `rate DECIMAL(5,2) NOT NULL UNIQUE CHECK (rate >= 0)`.
  - Adds `tax_category_id` to `menu_items`.
  - Adds `tax_rate` and `is_vat_inclusive` snapshots to `order_items`.
