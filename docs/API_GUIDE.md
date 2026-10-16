# Hướng Dẫn Sử Dụng API ROMS Backend

Tài liệu hướng dẫn chi tiết cách gọi, cấu trúc JSON Request/Response và quy trình xác thực cho các API đã hoàn thiện của hệ thống ROMS Backend.

---

## 1. Thông Tin Chung

- **Base URL:** `http://localhost:8080` (hoặc cổng cấu hình trong `.env` qua `SERVER_PORT`)
- **Định dạng dữ liệu:** `application/json` (UTF-8)
- **Chuẩn cấu trúc Response:** Tất cả API thành công và lỗi (trừ HTTP `204 No Content`) đều tuân theo chuẩn bọc envelope `ApiResponse<T>`:

### 1.1 Chuẩn Response Thành Công (`200 OK`, `201 Created`)
```json
{
  "success": true,
  "message": "Thông báo kết quả thành công",
  "data": { ... }
}
```

### 1.2 Chuẩn Response Thất Bại (`400`, `401`, `403`, `404`, `409`, `500`)
```json
{
  "success": false,
  "message": "Mô tả lý do thất bại",
  "error": {
    "code": "MÃ_LỖI",
    "details": null
  }
}
```

---

## 2. Tài Khoản Mẫu Có Sẵn Để Thử Nghiệm (Seed Data V3)

Tất cả các tài khoản mẫu dưới đây đều dùng chung mật khẩu mặc định: **`Password@123`**

| Tên đăng nhập | Họ và tên | Vai trò (`role`) | Gắn với bàn (`table_id`) | Ghi chú |
|---|---|---|---|---|
| `owner` | Nguyễn Văn Chủ | `OWNER` | `null` | Quản trị nhà hàng |
| `service01` | Trần Thị Thu Ngân | `SERVICE_STAFF` | `null` | Nhân viên phục vụ bàn |
| `service02` | Lê Hoàng Nam | `SERVICE_STAFF` | `null` | Nhân viên phục vụ bàn |
| `kitchen01` | Võ Quốc Bếp | `KITCHEN_STAFF` | `null` | Nhân viên bếp |
| `inventory01` | Phạm Thị Kho | `INVENTORY_STAFF` | `null` | Quản lý kho & nguyên liệu |
| `table_t01` | Tablet Bàn T01 | `TABLE_ACCOUNT` | `1` | Tablet khách tại bàn T01 |
| `table_t02` | Tablet Bàn T02 | `TABLE_ACCOUNT` | `2` | Tablet khách tại bàn T02 |
| `table_t03` ... `table_t10` | Tablet Bàn T03 → T10 | `TABLE_ACCOUNT` | `3` → `10` | Tablet khách tại bàn T03 → T10 |

---

## 3. Chi Tiết Các API Đã Triển Khai (`/api/auth`)

---

### 3.1 Đăng Nhập (`POST /api/auth/login`)

Xác thực thông tin tài khoản, cấp Access Token dạng Bearer JWT (thời hạn 15 phút) và tự động thiết lập Cookie `HttpOnly` lưu trữ Refresh Token (7 ngày cho nhân viên / 90 ngày cho tablet bàn).

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/login`
- **Yêu cầu xác thực:** Không (Public)
- **Headers:**
  - `Content-Type: application/json`

#### Request Body
```json
{
  "username": "owner",
  "password": "Password@123",
  "deviceId": "Chrome-PC-Admin"
}
```
*Ghi chú:* Trường `deviceId` là tùy chọn; nếu bỏ trống hệ thống sẽ tự động lấy từ header `User-Agent`.

#### Response Thành Công (`200 OK`)
- **Set-Cookie Header:**
  `refreshToken=ey...; Path=/api/auth; HttpOnly; SameSite=Lax; Max-Age=604800`
- **Response Body:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6Ik9XTkVSIiwic2lkIjoxLCJpYXQiOjE3NjA...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

#### Response Thất Bại Thường Gặp
- **Sai tài khoản hoặc mật khẩu (`401 Unauthorized`):**
  ```json
  {
    "success": false,
    "message": "Invalid username or password",
    "error": {
      "code": "UNAUTHORIZED",
      "details": null
    }
  }
  ```
  *(Hệ thống không tiết lộ việc username có tồn tại trong hệ thống hay không vì lý do bảo mật).*
- **Dữ liệu đầu vào không hợp lệ (`400 Bad Request`):**
  ```json
  {
    "success": false,
    "message": "Validation failed",
    "error": {
      "code": "VALIDATION_ERROR",
      "details": {
        "username": "Username cannot be blank",
        "password": "Password cannot be blank"
      }
    }
  }
  ```

---

### 3.2 Làm Mới Access Token (`POST /api/auth/refresh`)

Cấp Access Token mới và tự động xoay vòng (rotate) Refresh Token mà không yêu cầu người dùng phải đăng nhập lại.

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/refresh`
- **Yêu cầu xác thực:** Refresh Token Cookie
- **Headers:**
  - `Cookie: refreshToken=<chuỗi_refresh_token>` (Trình duyệt sẽ tự động gửi kèm cookie này nếu gọi từ client cùng domain / CORS credentials).
- **Request Body:** Không có body (Empty).

#### Response Thành Công (`200 OK`)
- **Set-Cookie Header:**
  Tự động cập nhật cookie mới với giá trị token vừa được xoay vòng.
- **Response Body:**
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6Ik9XTkVSIiwic2lkIjoxLCJpYXQiOjE3NjA...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

#### Response Thất Bại
- **Thiếu Cookie hoặc Cookie không hợp lệ (`401 Unauthorized`):**
  ```json
  {
    "success": false,
    "message": "Refresh token is invalid, expired, or has been revoked",
    "error": {
      "code": "UNAUTHORIZED",
      "details": null
    }
  }
  ```

---

### 3.3 Xem Thông Tin Tài Khoản Hiện Tại (`GET /api/auth/me`)

Lấy thông tin profile của người dùng đang đăng nhập thông qua Bearer Access Token.

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/auth/me`
- **Yêu cầu xác thực:** Cần Bearer Token
- **Headers:**
  - `Authorization: Bearer <accessToken>`

#### Response Thành Công (`200 OK`)
- **Trường hợp tài khoản Nhân viên / Quản lý (`OWNER`, `SERVICE_STAFF`, `KITCHEN_STAFF`, `INVENTORY_STAFF`):**
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": 1,
    "username": "owner",
    "fullName": "Nguyễn Văn Chủ",
    "role": "OWNER",
    "tableId": null
  }
}
```

- **Trường hợp tài khoản Tablet Bàn (`TABLE_ACCOUNT`):**
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": 6,
    "username": "table_t01",
    "fullName": "Tablet Bàn T01",
    "role": "TABLE_ACCOUNT",
    "tableId": 1
  }
}
```

#### Response Thất Bại
- **Thiếu token hoặc token hết hạn (`401 Unauthorized`):**
  ```json
  {
    "success": false,
    "message": "Full authentication is required to access this resource",
    "error": {
      "code": "UNAUTHORIZED",
      "details": null
    }
  }
  ```

---

### 3.4 Đăng Xuất (`POST /api/auth/logout`)

Thu hồi phiên đăng nhập hiện tại trong cơ sở dữ liệu và yêu cầu trình duyệt xóa Cookie `refreshToken`.

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/logout`
- **Yêu cầu xác thực:** Cần Bearer Token
- **Headers:**
  - `Authorization: Bearer <accessToken>`
- **Request Body:** Không có body (Empty).

#### Response Thành Công (`204 No Content`)
- **HTTP Status:** `204 No Content`
- **Set-Cookie Header:** `refreshToken=; Path=/api/auth; Max-Age=0` (Xóa cookie phía client).
- **Response Body:** Không có body (Empty).

---

## 4. Chi Tiết API Quản Lý Trạng Thái & Khả Năng Bán Món Ăn (`/api/menu-items`)

---

### 4.1 Cập Nhật Trạng Thái Món Ăn Thủ Công (`PATCH /api/menu-items/{id}/status`)

Cho phép Nhân viên Bếp (`KITCHEN_STAFF`) hoặc Chủ quán (`OWNER`) cập nhật trạng thái món ăn thủ công. Tự động xóa (invalidate) cache menu ngữ cảnh của Gemini trên Redis sau khi commit.

- **Method:** `PATCH`
- **URL:** `http://localhost:8080/api/menu-items/{id}/status`
- **Phân quyền:**
  - `OWNER`: Có quyền chuyển sang bất kỳ trạng thái nào (`AVAILABLE`, `TEMPORARILY_UNAVAILABLE`, `DISCONTINUED`).
  - `KITCHEN_STAFF`: Chỉ có quyền đổi giữa `AVAILABLE` và `TEMPORARILY_UNAVAILABLE`. Đặt đích `DISCONTINUED` hoặc sửa món hiện đang `DISCONTINUED` đều trả `403 Forbidden`; không thể bật lại món đã ngừng bán. Kiểm tra trạng thái hiện tại được thực hiện dưới khóa dòng trong transaction.
  - Các vai trò khác: Nhận lỗi `403 Forbidden`.
- **Headers:**
  - `Authorization: Bearer <access_token>`
  - `Content-Type: application/json`

#### Request Body
```json
{
  "status": "TEMPORARILY_UNAVAILABLE"
}
```
*Các giá trị `status` hợp lệ:* `AVAILABLE`, `TEMPORARILY_UNAVAILABLE`, `DISCONTINUED`.

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Menu item status updated successfully",
  "data": {
    "id": 1,
    "name": "Phở Bò Tái Nạm",
    "status": "TEMPORARILY_UNAVAILABLE",
    "updatedAt": "2026-10-13T22:20:00"
  }
}
```

#### Response Thất Bại Thường Gặp
- **Bếp cố tình đánh dấu món ngừng bán vĩnh viễn (`403 Forbidden`):**
  ```json
  {
    "success": false,
    "message": "Only OWNER can mark a menu item as DISCONTINUED",
    "error": {
      "code": "FORBIDDEN",
      "details": null
    }
  }
  ```
- **Không tìm thấy món ăn (`404 Not Found`):**
  ```json
  {
    "success": false,
    "message": "Menu item not found with id: 999",
    "error": {
      "code": "RESOURCE_NOT_FOUND",
      "details": null
    }
  }
  ```

---

### 4.2 Kiểm Tra Khả Năng Phục Vụ Live (`GET /api/menu-items/{id}/sellability`)

Kiểm tra trực tiếp từ PostgreSQL xem món ăn có đủ điều kiện bán hay không dựa trên trạng thái món và số lượng tồn kho thực tế của các nguyên liệu trong công thức (BOM). **Không** làm thay đổi trạng thái món trong DB và **không** sử dụng hạn mức `available_quantity`.

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/menu-items/{id}/sellability?quantity=2`
- **Query Parameters:**
  - `quantity`: Số lượng phần ăn dự kiến cần đặt (Mặc định: `1`, tối thiểu: `1`).
- **Phân quyền:** Mọi tài khoản đã đăng nhập (`TABLE_ACCOUNT`, `SERVICE_STAFF`, `KITCHEN_STAFF`, `OWNER`, `INVENTORY_STAFF`).
- **Headers:**
  - `Authorization: Bearer <access_token>`

#### Response Thành Công — Đủ Nguyên Liệu (`200 OK`)
```json
{
  "success": true,
  "message": "Menu item sellability checked successfully",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Tái Nạm",
    "status": "AVAILABLE",
    "sellable": true,
    "reason": null,
    "requestedQuantity": 2,
    "maxPossibleQuantity": 133,
    "insufficientIngredients": []
  }
}
```

#### Response Thành Công — Thiếu Nguyên Liệu / Tạm Hết Hàng (`200 OK`)
```json
{
  "success": true,
  "message": "Menu item sellability checked successfully",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Tái Nạm",
    "status": "AVAILABLE",
    "sellable": false,
    "reason": "Insufficient ingredient stock: Thịt bò phi lê",
    "requestedQuantity": 500,
    "maxPossibleQuantity": 133,
    "insufficientIngredients": [
      {
        "ingredientId": 1,
        "ingredientName": "Thịt bò phi lê",
        "unit": "kg",
        "requiredQuantity": 75.000,
        "currentStock": 20.000
      }
    ]
  }
}
```

---

## 5. Chi Tiết Các API Quản Lý Chi Phí (`/api/expenses`)

Tất cả các API chi phí yêu cầu quyền **`OWNER`** (`Authorization: Bearer <token_chu_nha_hang>`).

### 5.1 Tạo Chi Phí Vận Hành Mới (`POST /api/expenses`)
- **Headers:** `Authorization: Bearer <accessToken>`, `Content-Type: application/json`
- **Request Body:**
```json
{
  "name": "Tiền điện tháng 10/2026",
  "amount": 3500000.00,
  "expenseDate": "2026-10-15",
  "note": "Hóa đơn điện lực EVN"
}
```
- **Response (`201 Created`):**
```json
{
  "success": true,
  "message": "Expense created successfully",
  "data": {
    "id": 1,
    "name": "Tiền điện tháng 10/2026",
    "amount": 3500000.00,
    "expenseDate": "2026-10-15",
    "note": "Hóa đơn điện lực EVN",
    "createdAt": "2026-10-15T08:30:00",
    "createdBy": "owner",
    "cancelledAt": null,
    "cancelledBy": null,
    "cancelReason": null,
    "cancelled": false
  }
}
```

### 5.2 Lấy Danh Sách Chi Phí (`GET /api/expenses`)
- **Query Params:**
  - `startDate`: Ngày bắt đầu (`YYYY-MM-DD`, tùy chọn)
  - `endDate`: Ngày kết thúc (`YYYY-MM-DD`, tùy chọn)
  - `status`: `ALL` (mặc định), `ACTIVE` (chưa hủy), `CANCELLED` (đã hủy)
  - `page`: Số trang (0-indexed, mặc định 0)
  - `size`: Kích thước trang (mặc định 20)
  - `sort`: Trường sắp xếp (ví dụ: `expenseDate,desc`)
- **Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Expenses retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Tiền điện tháng 10/2026",
        "amount": 3500000.00,
        "expenseDate": "2026-10-15",
        "note": "Hóa đơn điện lực EVN",
        "createdAt": "2026-10-15T08:30:00",
        "createdBy": "owner",
        "cancelled": false
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 5.3 Cập Nhật Chi Phí (`PUT /api/expenses/{id}`)
- **Request Body:**
```json
{
  "name": "Tiền điện tháng 10/2026 (đã bổ sung)",
  "amount": 3750000.00,
  "expenseDate": "2026-10-15",
  "note": "Bổ sung phí phạt chậm nộp"
}
```
- **Response:** `200 OK` nếu thành công; `409 Conflict` nếu chi phí đã bị hủy.

### 5.4 Hủy Chi Phí (`POST /api/expenses/{id}/cancel`)
- Hủy mềm (soft-cancel), lưu lại vết kiểm toán `cancelled_at`, `cancelled_by`, `cancel_reason`.
- **Request Body:**
```json
{
  "reason": "Nhập nhầm số tiền và trùng hóa đơn"
}
```
- **Response:** `200 OK` nếu thành công; `409 Conflict` nếu chi phí này trước đó đã bị hủy.

---

## 6. Chi Tiết Các API Thống Kê & Dashboard (`/api/dashboard`)

Tất cả các endpoint thống kê yêu cầu quyền **`OWNER`**.

### 6.1 Tổng Quan Dashboard (`GET /api/dashboard/overview`)
- **Query Params:** `startDate` (`YYYY-MM-DD`), `endDate` (`YYYY-MM-DD`). Nếu không truyền, mặc định là 30 ngày gần nhất tính đến hôm nay.
- **Quy tắc tính toán:**
  - `totalRevenue`: Tổng tiền món ăn từ các đơn hàng thành công (`COMPLETED`).
  - `totalExpenses`: Tổng chi phí vận hành còn hiệu lực (bỏ qua chi phí đã hủy).
  - `totalPurchases`: Tổng chi phí nhập hàng trong kỳ.
  - `netProfit`: Lợi nhuận ròng = `totalRevenue - (totalExpenses + totalPurchases)`.
- **Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Dashboard overview retrieved successfully",
  "data": {
    "startDate": "2026-10-01",
    "endDate": "2026-10-15",
    "totalRevenue": 50000000.00,
    "totalExpenses": 10000000.00,
    "totalPurchases": 15000000.00,
    "netProfit": 25000000.00,
    "completedOrdersCount": 120,
    "cancelledOrdersCount": 5,
    "activeSessionsCount": 8,
    "lowStockIngredientsCount": 2
  }
}
```

### 6.2 Top Món Bán Chạy Nhất (`GET /api/dashboard/best-selling`)
- **Query Params:** `startDate`, `endDate`, `limit` (mặc định: 5).
- **Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Best selling dishes retrieved successfully",
  "data": [
    {
      "menuItemId": 1,
      "menuItemName": "Phở Bò Tái Nạm",
      "quantitySold": 85,
      "totalRevenue": 5525000.00
    },
    {
      "menuItemId": 2,
      "menuItemName": "Cơm Tấm Sườn Bì Chả",
      "quantitySold": 60,
      "totalRevenue": 3300000.00
    }
  ]
}
```

### 6.3 Biến Động Doanh Thu & Chi Phí Hàng Ngày (`GET /api/dashboard/trends`)
- **Query Params:** `startDate`, `endDate`.
- **Response (`200 OK`):**
```json
{
  "success": true,
  "message": "Daily trends retrieved successfully",
  "data": [
    {
      "date": "2026-10-01",
      "revenue": 1000000.00,
      "expenses": 300000.00,
      "purchases": 200000.00,
      "netProfit": 500000.00
    }
  ]
}
```

### 6.4 Xuất File Báo Cáo Tài Chính Hàng Ngày CSV (`GET /api/dashboard/export`)
- **Query Params:** `startDate`, `endDate`.
- **Response:** File `text/csv; charset=UTF-8` đính kèm có UTF-8 BOM (`\uEF\uBB\uBF`), header `Content-Disposition: attachment; filename="daily-financial-report-YYYY-MM-DD.csv"`. Mở trực tiếp bằng Microsoft Excel không bị lỗi font tiếng Việt.

---

## 7. Hướng Dẫn Kết Nối WebSocket / STOMP Realtime

Hệ thống đã hoàn thiện broker STOMP in-memory hỗ trợ cập nhật dữ liệu tức thì cho Bếp, Bàn ăn và Nhân viên phục vụ.

- **WebSocket URL:** `ws://localhost:8080/ws` (hoặc qua SockJS: `http://localhost:8080/ws`)
- **Xác thực lúc kết nối (CONNECT Frame):**
  Truyền Access Token trong header của STOMP:
  ```
  CONNECT
  accept-version:1.2,1.1,1.0
  heart-beat:10000,10000
  Authorization:Bearer <accessToken>

  ^@
  ```

### Các Kênh Đăng Ký (SUBSCRIBE Frame)

| Đường dẫn (Destination) | Vai trò được phép nghe | Nội dung tin nhắn nhận được |
|---|---|---|
| `/topic/kitchen/orders` | `KITCHEN_STAFF`, `OWNER` | Thông báo khi có món/đơn mới tạo hoặc đơn bị hủy |
| `/topic/session/{sessionId}/orders` | `SERVICE_STAFF`, `OWNER`, `TABLE_ACCOUNT` (chỉ bàn của mình) | Trạng thái đơn, món đang nấu, món đã sẵn sàng phục vụ |
| `/topic/tables` | `SERVICE_STAFF`, `OWNER` | Trạng thái mở/đóng bàn, chuyển bàn |
| `/user/queue/notifications` | Bất kỳ user đã xác thực | Tin nhắn/thông báo gửi riêng cho cá nhân |

---

## 8. Mẫu Gọi API Bằng cURL

### 8.1 Đăng nhập (Lưu Cookie tự động ra file `cookies.txt`)
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"owner","password":"Password@123"}'
```

### 8.2 Xem Profile hiện tại
```bash
curl -X GET "http://localhost:8080/api/ingredients?isActive=true&page=0&size=20" \
  -H "Authorization: Bearer <access_token>"
```

### 8.3 Làm mới Access Token (Gửi kèm Cookie từ file)
```bash
curl -X POST http://localhost:8080/api/purchases \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "purchaseDate": "2026-10-14",
    "note": "Nhập thịt bò",
    "items": [
      {"ingredientId": 1, "quantity": 10.0, "unitPrice": 250000.0}
    ]
  }'
```

### 8.4 Đăng xuất
```bash
curl -X PATCH http://localhost:8080/api/purchases/1/items/1 \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 9.0,
    "unitPrice": 250000.0,
    "reason": "Cân lại bị thiếu 1kg"
  }'
```

### 8.5 Đổi trạng thái món ăn (Kitchen/Owner)
```bash
curl -X PATCH http://localhost:8080/api/menu-items/1/status \
  -H "Authorization: Bearer <access_token_nhận_được>" \
  -H "Content-Type: application/json" \
  -d '{"status":"TEMPORARILY_UNAVAILABLE"}'
```

### 8.6 Kiểm tra khả năng phục vụ món ăn (Tất cả vai trò)
```bash
curl -X GET "http://localhost:8080/api/menu-items/1/sellability?quantity=2" \
  -H "Authorization: Bearer <access_token_nhận_được>"
```

### 8.7 Tạo Chi Phí Mới (Owner)
```bash
curl -X POST http://localhost:8080/api/expenses \
  -H "Authorization: Bearer <access_token_owner>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Tiền gas tháng 10","amount":1200000,"expenseDate":"2026-10-15","note":"Bình gas 45kg"}'
```

### 8.8 Lấy Tổng Quan Dashboard (Owner)
```bash
curl -X GET "http://localhost:8080/api/dashboard/overview?startDate=2026-10-01&endDate=2026-10-15" \
  -H "Authorization: Bearer <access_token_owner>"
```

### 8.9 Xuất Báo Cáo Tài Chính CSV (Owner)
```bash
curl -X GET "http://localhost:8080/api/dashboard/export?startDate=2026-10-01&endDate=2026-10-15" \
  -H "Authorization: Bearer <access_token_owner>" \
  -o daily_report.csv
```

## 9. API Catalog và Menu Tablet — BE-007

Các API đều dùng `Authorization: Bearer <accessToken>` và envelope chung. Mọi tài khoản đã xác thực được đọc; chỉ OWNER được ghi danh mục, món và ảnh. Không có xóa cứng danh mục/món: category ngừng hoạt động bằng `isActive=false`; món ngừng bán qua PATCH status của BE-008.

### 9.1 Danh Mục

- `GET /api/categories`: `ApiResponse<PageResponse<CategoryResponse>>`; page bắt đầu 0, mặc định size 20, tối đa 100, sort mặc định `displayOrder,asc` và thêm `id,asc` để ổn định.
- `GET /api/categories/{id}`: chi tiết CategoryResponse.
- TABLE_ACCOUNT chỉ thấy category active; chi tiết inactive trả 404 như category không tồn tại. Staff/Owner được đọc cả inactive; không cần active service session để đọc catalog.
- `POST /api/categories` trả 201 + Location; `PUT /api/categories/{id}` trả 200. Payload đầy đủ: `name`, `description`, `displayOrder`, `isActive`. Tên không trắng, tối đa 100 ký tự; description nullable, tối đa 255; displayOrder/isActive bắt buộc.
- CategoryResponse gồm `id`, `name`, `description`, `displayOrder`, `isActive`.

### 9.2 Đọc Danh Sách và Chi Tiết Món

| Method | Endpoint | Response |
|---|---|---|
| GET | `/api/menu-items` | 200, ApiResponse với PageResponse trong data |
| GET | `/api/menu-items/{id}` | 200, ApiResponse với MenuItemReadResponse trong data |

Danh sách nhận `page`, `size`, `sort`, `categoryId` (optional, dương), `status` (optional, một giá trị enum), `quantity` (mặc định 1, phải dương). Mặc định page 0, size 20; size lớn hơn 100 được giới hạn còn 100. Sort hỗ trợ `id`, `name`, `price`, `status`, `isFeatured`; mặc định `name,asc`, thêm `id,asc` nếu chưa có sort id. Category không có món khớp trả trang rỗng. Chi tiết nhận `quantity` theo cùng quy tắc.

Quyền xem status: OWNER đọc mọi trạng thái; KITCHEN_STAFF và SERVICE_STAFF chỉ đọc AVAILABLE hoặc TEMPORARILY_UNAVAILABLE; TABLE_ACCOUNT chỉ đọc AVAILABLE trong category active. INVENTORY_STAFF giữ quyền đọc mọi trạng thái như trước. Staff/Owner vẫn đọc được category inactive. Các điều kiện status, categoryId và category active của tablet được kết hợp trong truy vấn DB trước cả phân trang và count, nên totalElements/totalPages không tính món bị ẩn. Không truyền status trả tất cả trạng thái trong phạm vi role; truyền status ngoài quyền trả 403 FORBIDDEN (không bỏ qua filter, không mở rộng quyền). Giá trị không thuộc enum trả 400 INVALID_REQUEST_PARAMETER. Chi tiết món dùng cùng quyền xem; món ngoài quyền/không tồn tại đều trả 404. Món AVAILABLE nhưng thiếu nguyên liệu vẫn hiển thị với availability.sellable=false.

MenuItemReadResponse gồm thông tin món, `category` (CategoryResponse), `images` và `availability`. Ảnh sắp theo displayOrder rồi id; không có ảnh trả `[]`. Availability gọi service BE-008 từ PostgreSQL cho mỗi món ở mỗi lần đọc; không cache, không trừ kho và không tạo Order. Chỉ trả kết quả khả năng bán, không trả insufficientIngredients/currentStock chi tiết trong DTO đọc catalog. Kết quả tại thời điểm đọc không giữ chỗ; tạo Order phải kiểm tra lại tồn kho.

```json
{
  "success": true,
  "message": "Menu item retrieved successfully",
  "data": {
    "id": 1,
    "category": {"id": 1, "name": "Món Chính", "description": null, "displayOrder": 1, "isActive": true},
    "name": "Phở Bò",
    "description": "Phở bò truyền thống",
    "price": 65000.00,
    "isFeatured": true,
    "status": "AVAILABLE",
    "images": [],
    "availability": {"sellable": true, "reason": null, "requestedQuantity": 1, "maxPossibleQuantity": 10}
  }
}
```

Danh sách dùng cùng DTO trong `data.content`, message `Menu items retrieved successfully`. `maxPossibleQuantity` là số lượng suy ra theo BE-008, có thể null; không phải quota/available_quantity lưu trên món.

```bash
curl "http://localhost:8080/api/menu-items?categoryId=1&status=AVAILABLE&page=0&size=20&sort=price,asc&quantity=2" \
  -H "Authorization: Bearer <accessToken>"
```

401 khi thiếu/sai/hết hạn JWT, session bị thu hồi hoặc account inactive; 400 cho ID/query sai kiểu, status không hợp lệ, ID/quantity không dương hoặc sort field không hỗ trợ; 403 cho status filter ngoài quyền; 404 cho chi tiết không tồn tại/bị ẩn. Không đổi response DTO, shared auth hoặc logic recipe/tồn kho.

### 9.3 Ghi Món và Ảnh

POST `/api/menu-items` trả 201 + Location; PUT `/api/menu-items/{id}` trả 200. Payload: `categoryId`, `name`, `description`, `price`, `isFeatured`; category phải tồn tại, tên không trắng tối đa 255 ký tự, giá không âm và phù hợp NUMERIC(12,2), isFeatured bắt buộc. Tạo món mặc định AVAILABLE; PUT không sửa status hoặc giá snapshot OrderItem. DTO ghi trả MenuItemResponse hiện có, không đổi thành DTO đọc.

POST multipart `/api/menu-items/{menuItemId}/images` nhận part `file`, parameter `displayOrder` mặc định 0, trả 201 + Location và MenuItemImageResponse. DELETE `/api/menu-items/{menuItemId}/images/{imageId}` trả 204 body rỗng; chỉ được thao tác ảnh thuộc đúng món. Chấp nhận JPEG/PNG/WebP tối đa 5 MB, kiểm tra content type/chữ ký, provider kiểm tra ảnh thực tế. Multipart request tối đa 6 MB.

Credentials: `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` trong biến môi trường hoặc .env được Git bỏ qua. Public ID của ảnh API tạo: `roms/menu-items/{menuItemId}/images/{imageId}`, không thêm schema. Rollback upload thử xóa asset bù; provider xóa lỗi giữ metadata để retry. Cleanup không phải transaction phân tán: mất process hoặc provider lỗi có thể để lại orphan; xóa provider thành công nhưng DB rollback có thể để metadata trỏ ảnh mất. Lần xóa lại chấp nhận provider not found. Ảnh legacy cần đối chiếu public ID trước khi dùng API xóa.

Catalog/ảnh invalidation context sau commit, không khi rollback; tái sử dụng primitive Redis hiện có. Menu/category và ảnh đọc theo truy vấn catalog + batch ảnh; live sellability hiện dùng API một món của BE-008 nên số truy vấn recipe tăng theo số món trong trang (trang được giới hạn 100). Không thêm thuật toán hoặc batch contract vào BE-008 của owner khác.

### 9.4 Kiểm Chứng và Review

Test BE-007 gồm service/API, JWT filter và SecurityConfig thật với repository mock, integration BE-008 thật với recipe/stock mock, kiểm tra truy vấn PostgreSQL chỉ đọc và Cloudinary upload/delete fixture riêng. Test database bật `spring.flyway.enabled=false` và `ddl-auto=validate`; không tạo fixture DB hoặc chạy migration.

Hai test ngoài hệ thống là opt-in: `MenuCatalogPostgresReadTest` cần `-Droms.test.postgres=true`; `CloudinaryLiveSmokeTest` cần `-Droms.test.cloudinary=true`. Cloudinary fixture có public ID UUID dưới `roms/be007-smoke/` và luôn thử cleanup; không ghi bảng menu_item_images. Credentials và error details provider không xuất ra log kiểm thử.

BE-007 hoàn tất phần implementation theo hợp đồng hiện được duyệt, chờ review trước khi đánh dấu Done. Chưa chạy các integration test ghi DB/order/kitchen của owner khác trong lượt này.

Kết quả ngày 2026-10-15: biên dịch thành công, 165/165 test regression không dùng DB pass (54 test BE-007), thêm 2/2 kiểm tra PostgreSQL/Cloudinary thật pass; tổng 56 kiểm tra BE-007. Dùng Surefire 3.2.5 trực tiếp do runner mặc định 3.5.6 thiếu thành phần trong cache, không sửa pom để đổi runner.
