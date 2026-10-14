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

## 4. Hướng Dẫn Kết Nối WebSocket / STOMP Realtime

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

## 5. API Quản Lý Nguyên Liệu, Kiểm Kê & Biến Động Kho (BE-014)

---

### 5.1 Lấy Danh Sách Nguyên Liệu (`GET /api/ingredients`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`, `KITCHEN_STAFF`
- **Query Params:**
  - `isActive` (tùy chọn, boolean): `true` (chỉ lấy đang dùng) hoặc `false` (đã vô hiệu hóa). Bỏ trống để lấy tất cả.
  - `page` (tùy chọn, mặc định: 0)
  - `size` (tùy chọn, mặc định: 20)
  - `sort` (tùy chọn, mặc định: `name,asc`)

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Thịt bò tái",
        "unit": "kg",
        "currentStock": 15.500,
        "minimumStock": 5.000,
        "isActive": true,
        "stockStatus": "NORMAL",
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```
*Ghi chú về `stockStatus` (tính toán động, không lưu cứng vào DB):*
- `OUT_OF_STOCK`: Khi `currentStock <= 0`.
- `LOW`: Khi `currentStock > 0` và `currentStock <= minimumStock`.
- `NORMAL`: Các trường hợp còn lại.

---

### 5.2 Lấy Chi Tiết Nguyên Liệu (`GET /api/ingredients/{id}`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`, `KITCHEN_STAFF`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": 1,
    "name": "Thịt bò tái",
    "unit": "kg",
    "currentStock": 15.500,
    "minimumStock": 5.000,
    "isActive": true,
    "stockStatus": "NORMAL",
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

---

### 5.3 Thêm Mới Nguyên Liệu (`POST /api/ingredients`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Request Body:**
```json
{
  "name": "Hành lá",
  "unit": "kg",
  "minimumStock": 2.000
}
```
*(Số lượng `currentStock` ban đầu luôn được thiết lập mặc định là 0. Tồn kho chỉ được gia tăng qua phiếu nhập hàng hoặc điều chỉnh qua phiếu kiểm kê).*

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "Ingredient created successfully",
  "data": {
    "id": 2,
    "name": "Hành lá",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 2.000,
    "isActive": true,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:10:00"
  }
}
```

---

### 5.4 Cập Nhật Thông Tin Nguyên Liệu (`PUT /api/ingredients/{id}`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Request Body:**
```json
{
  "name": "Hành lá Đà Lạt",
  "unit": "kg",
  "minimumStock": 3.000
}
```
*(Endpoint này chỉ sửa metadata của nguyên liệu, bảo toàn số lượng tồn kho hiện tại).*

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Ingredient updated successfully",
  "data": {
    "id": 2,
    "name": "Hành lá Đà Lạt",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 3.000,
    "isActive": true,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:15:00"
  }
}
```

---

### 5.5 Vô Hiệu Hóa Nguyên Liệu (`PATCH /api/ingredients/{id}/deactivate`)

- **Vai trò được phép:** `OWNER`
- **Ràng buộc nghiệp vụ:** Hệ thống chặn không cho vô hiệu hóa nếu nguyên liệu đang nằm trong Recipe của bất kỳ món ăn nào chưa ngừng kinh doanh (`status <> 'DISCONTINUED'`). Trả về `409 Conflict`.

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Ingredient deactivated successfully",
  "data": {
    "id": 2,
    "name": "Hành lá Đà Lạt",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 3.000,
    "isActive": false,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:20:00"
  }
}
```

---

### 5.6 Tạo Phiếu Kiểm Kê Kho (`POST /api/inventory-checks`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Cơ chế hoạt động:** Trong 1 transaction duy nhất:
  1. Khóa bi quan (`PESSIMISTIC_WRITE`) các nguyên liệu liên quan.
  2. Tính chênh lệch `difference = actualQuantity - systemQuantity`.
  3. Cập nhật `currentStock = actualQuantity`.
  4. Nếu có chênh lệch, tự động ghi `StockMovement` (type: `INVENTORY_ADJUSTMENT`, `balanceAfter = actualQuantity`) bù trừ.
- **Request Body:**
```json
{
  "checkedAt": "2026-10-14T00:00:00",
  "note": "Kiểm kê đầu ca sáng",
  "items": [
    {
      "ingredientId": 1,
      "actualQuantity": 14.000,
      "reason": "Hao hụt tự nhiên do sơ chế"
    }
  ]
}
```

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "Inventory check created successfully",
  "data": {
    "id": 1,
    "checkedById": 1,
    "checkedByName": "Nguyễn Văn Chủ",
    "checkedAt": "2026-10-14T00:00:00",
    "note": "Kiểm kê đầu ca sáng",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "systemQuantity": 15.500,
        "actualQuantity": 14.000,
        "difference": -1.500,
        "reason": "Hao hụt tự nhiên do sơ chế",
        "createdAt": "2026-10-14T00:00:00"
      }
    ],
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

---

### 5.7 Xem Danh Sách / Chi Tiết Kiểm Kê Kho

- **Lấy danh sách:** `GET /api/inventory-checks?page=0&size=20` (`OWNER`, `INVENTORY_STAFF`)
- **Lấy chi tiết:** `GET /api/inventory-checks/{id}` (`OWNER`, `INVENTORY_STAFF`)

---

### 5.8 Lịch Sử Biến Động Kho (`GET /api/stock-movements`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Query Params:**
  - `ingredientId` (tùy chọn): ID nguyên liệu cần xem lịch sử.
  - `movementType` (tùy chọn): `PURCHASE`, `ORDER_CONSUMPTION`, hoặc `INVENTORY_ADJUSTMENT`.
  - `page`, `size`, `sort` (mặc định: `occurredAt,desc`).

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "id": 10,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "movementType": "INVENTORY_ADJUSTMENT",
        "quantityChange": -1.500,
        "balanceAfter": 14.000,
        "purchaseItemId": null,
        "orderItemId": null,
        "inventoryCheckItemId": 1,
        "note": "Hao hụt tự nhiên do sơ chế",
        "occurredAt": "2026-10-14T00:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 6. API Quản Lý Phiếu Nhập Hàng & Điều Chỉnh Bù Trừ (BE-015)

---

### 6.1 Lấy Danh Sách Phiếu Nhập (`GET /api/purchases`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Query Params:**
  - `fromDate` (tùy chọn, định dạng `YYYY-MM-DD`)
  - `toDate` (tùy chọn, định dạng `YYYY-MM-DD`)
  - `page`, `size`, `sort` (mặc định: `purchaseDate,desc`)

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "id": 1,
        "purchaseDate": "2026-10-14",
        "totalAmount": 3750000.00,
        "createdById": 1,
        "createdByName": "Nguyễn Văn Chủ",
        "note": "Nhập thịt bò tươi buổi sáng",
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 6.2 Lấy Chi Tiết Phiếu Nhập (`GET /api/purchases/{id}`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": 1,
    "purchaseDate": "2026-10-14",
    "totalAmount": 3750000.00,
    "createdById": 1,
    "createdByName": "Nguyễn Văn Chủ",
    "note": "Nhập thịt bò tươi buổi sáng",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 15.000,
        "unitPrice": 250000.00,
        "subtotal": 3750000.00,
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

---

### 6.3 Tạo Phiếu Nhập Hàng (`POST /api/purchases`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Cơ chế hoạt động:**
  - Khóa bi quan (`PESSIMISTIC_WRITE`) các nguyên liệu.
  - Tính `subtotal = quantity * unitPrice`, cộng dồn vào `totalAmount`.
  - Tăng số lượng tồn kho `currentStock += quantity`.
  - Ghi bản ghi `StockMovement` (type: `PURCHASE`).
- **Request Body:**
```json
{
  "purchaseDate": "2026-10-14",
  "note": "Nhập hàng từ NCC Minh Thư",
  "items": [
    {
      "ingredientId": 1,
      "quantity": 15.000,
      "unitPrice": 250000.00
    }
  ]
}
```

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "Purchase created successfully",
  "data": {
    "id": 1,
    "purchaseDate": "2026-10-14",
    "totalAmount": 3750000.00,
    "createdById": 1,
    "createdByName": "Nguyễn Văn Chủ",
    "note": "Nhập hàng từ NCC Minh Thư",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 15.000,
        "unitPrice": 250000.00,
        "subtotal": 3750000.00,
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

---

### 6.4 Điều Chỉnh Dòng Hàng Phiếu Nhập (`PATCH /api/purchases/{purchaseId}/items/{itemId}`)

- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Cơ chế hoạt động (Correction Logic):**
  1. Tính độ lệch: $\Delta = \text{newQuantity} - \text{oldQuantity}$.
  2. Cập nhật tồn kho: $\text{newStock} = \text{currentStock} + \Delta$.
  3. Nếu $\text{newStock} < 0$, từ chối với lỗi `409 Conflict` (không thể giảm số lượng nếu tồn kho thực tế hiện tại không đủ).
  4. Cập nhật số lượng, đơn giá, tính lại `subtotal` và `totalAmount` của phiếu nhập.
  5. Tự động ghi nhận một bản ghi `StockMovement` bù trừ mới (type `PURCHASE`, `quantityChange = delta`, `balanceAfter = newStock`) kèm ghi chú lý do điều chỉnh (`reason`).
- **Request Body:**
```json
{
  "quantity": 12.000,
  "unitPrice": 250000.00,
  "reason": "Thực tế cân lại bị hao hụt 3kg"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Purchase item corrected successfully",
  "data": {
    "id": 1,
    "purchaseDate": "2026-10-14",
    "totalAmount": 3000000.00,
    "createdById": 1,
    "createdByName": "Nguyễn Văn Chủ",
    "note": "Nhập hàng từ NCC Minh Thư",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 12.000,
        "unitPrice": 250000.00,
        "subtotal": 3000000.00,
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:05:00"
      }
    ],
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:05:00"
  }
}
```

---

## 7. API Quản Lý Công Thức Món Ăn - Recipe/BOM (BE-033)

> *Lưu ý: Mọi thao tác thêm/sửa/xóa công thức đều tự động xóa (invalidate) cache thực đơn của trợ lý Gemini trên Redis (`menu:context:v1`) để AI luôn nắm bắt đúng định lượng.*

---

### 7.1 Xem Công Thức Của Món Ăn (`GET /api/menu-items/{menuItemId}/recipe`)

- **Vai trò được phép:** `OWNER`, `KITCHEN_STAFF`, `INVENTORY_STAFF`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Đặc Biệt",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantityRequired": 0.150
      }
    ]
  }
}
```

---

### 7.2 Thay Thế Toàn Bộ Công Thức Món (`PUT /api/menu-items/{menuItemId}/recipe`)

- **Vai trò được phép:** `OWNER`
- **Request Body:**
```json
{
  "items": [
    {
      "ingredientId": 1,
      "quantityRequired": 0.150
    },
    {
      "ingredientId": 2,
      "quantityRequired": 0.050
    }
  ]
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Recipe updated successfully",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Đặc Biệt",
    "items": [
      {
        "id": 2,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantityRequired": 0.150
      },
      {
        "id": 3,
        "ingredientId": 2,
        "ingredientName": "Hành lá",
        "unit": "kg",
        "quantityRequired": 0.050
      }
    ]
  }
}
```

---

### 7.3 Thêm 1 Nguyên Liệu Vào Công Thức (`POST /api/menu-items/{menuItemId}/recipe/items`)

- **Vai trò được phép:** `OWNER`
- **Request Body:**
```json
{
  "ingredientId": 2,
  "quantityRequired": 0.050
}
```
*(Nếu nguyên liệu đã có sẵn trong công thức của món, trả về `409 Conflict: DUPLICATE_RECIPE_ITEM`)*.

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "Recipe item added successfully",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Đặc Biệt",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantityRequired": 0.150
      },
      {
        "id": 2,
        "ingredientId": 2,
        "ingredientName": "Hành lá",
        "unit": "kg",
        "quantityRequired": 0.050
      }
    ]
  }
}
```

---

### 7.4 Sửa Định Lượng 1 Nguyên Liệu (`PATCH /api/menu-items/{menuItemId}/recipe/items/{ingredientId}`)

- **Vai trò được phép:** `OWNER`
- **Request Body:**
```json
{
  "quantityRequired": 0.200
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Recipe item updated successfully",
  "data": {
    "menuItemId": 1,
    "menuItemName": "Phở Bò Đặc Biệt",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantityRequired": 0.200
      }
    ]
  }
}
```

---

### 7.5 Xóa Nguyên Liệu Khỏi Công Thức (`DELETE /api/menu-items/{menuItemId}/recipe/items/{ingredientId}`)

- **Vai trò được phép:** `OWNER`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Recipe item deleted successfully",
  "data": null
}
```

---

## 8. Mẫu Gọi API Bằng cURL

### 8.1 Đăng nhập lấy Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"owner","password":"Password@123"}'
```

### 8.2 Xem danh sách nguyên liệu
```bash
curl -X GET "http://localhost:8080/api/ingredients?isActive=true&page=0&size=20" \
  -H "Authorization: Bearer <access_token>"
```

### 8.3 Tạo phiếu nhập hàng
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

### 8.4 Điều chỉnh số lượng dòng hàng phiếu nhập
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

### 8.5 Xem công thức món ăn
```bash
curl -X GET http://localhost:8080/api/menu-items/1/recipe \
  -H "Authorization: Bearer <access_token>"
```

