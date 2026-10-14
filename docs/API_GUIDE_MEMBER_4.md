# Hướng Dẫn Sử Dụng API — Member 4 (Inventory, Recipes & Purchases)

Tài liệu hướng dẫn chi tiết toàn bộ các API do **Member 4** phụ trách trong hệ thống **ROMS Backend**, bao gồm:
- **BE-014:** Quản lý nguyên liệu (Ingredient), Tồn kho tối thiểu, Cảnh báo kho, Kiểm kê kho (Inventory Check), Lịch sử biến động kho (Stock Movement).
- **BE-015:** Quản lý phiếu nhập hàng (Purchases) & Điều chỉnh sai sót (Correction) với cập nhật bù trừ kho traceable.
- **BE-033:** Quản lý công thức định lượng món ăn (Recipe/BOM) & Đồng bộ Invalidate cache thực đơn Gemini trên Redis.

---

## 1. Thông Tin Chung & Quy Chuẩn Gọi API

### 1.1 Kết Nối Cơ Bản
- **Base URL:** `http://localhost:8080` (hoặc cổng cấu hình trong `.env` qua `SERVER_PORT`)
- **Định dạng dữ liệu:** `application/json` (UTF-8)
- **Header xác thực bắt buộc (cho tất cả API trừ Public):**
  ```http
  Authorization: Bearer <access_token>
  ```

### 1.2 Cấu Trúc Envelope Chuẩn (`ApiResponse<T>`)

#### Phản hồi thành công (`200 OK`, `201 Created`):
```json
{
  "success": true,
  "message": "Thông báo kết quả thành công",
  "data": { ... }
}
```

#### Phản hồi phân trang (`PageResponse<T>` nằm trong `data`):
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
}
```

#### Phản hồi lỗi (`400`, `401`, `403`, `404`, `409`):
```json
{
  "success": false,
  "message": "Mô tả lỗi chi tiết",
  "error": {
    "code": "MÃ_LỖI_NGHIỆP_VỤ",
    "details": null
  }
}
```

---

## 2. Tài Khoản Phục Vụ Kiểm Thử (Seed Data V3)

Tất cả tài khoản thử nghiệm đều dùng mật khẩu mặc định: **`Password@123`**

| Tên đăng nhập | Họ và tên | Vai trò (`role`) | Quyền hạn đối với Member 4 |
|---|---|---|---|
| `owner` | Nguyễn Văn Chủ | `OWNER` | **Toàn quyền:** Quản lý nguyên liệu, duyệt phiếu nhập, sửa phiếu nhập, kiểm kê kho, thiết lập công thức món ăn. |
| `inventory01` | Phạm Thị Kho | `INVENTORY_STAFF` | **Quản lý kho:** Xem/tạo/sửa nguyên liệu (trừ deactivate), tạo/sửa phiếu nhập, kiểm kê kho, xem lịch sử kho, xem công thức. |
| `kitchen01` | Võ Quốc Bếp | `KITCHEN_STAFF` | **Bếp trưởng:** Chỉ có quyền **xem** danh sách nguyên liệu và xem công thức món ăn (không được sửa tồn kho). |

> **Cách lấy Token để gọi API:** Gửi `POST /api/auth/login` với username/password tương ứng, lấy `data.accessToken` gắn vào header `Authorization: Bearer <token>`.

---

## 3. API Quản Lý Nguyên Liệu (`/api/ingredients`) — BE-014

### 3.1 Lấy Danh Sách Nguyên Liệu (`GET /api/ingredients`)

Lấy danh sách nguyên liệu có phân trang, hỗ trợ lọc theo trạng thái hoạt động.

- **Method:** `GET`
- **URL:** `/api/ingredients`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`, `KITCHEN_STAFF`
- **Query Parameters:**
  - `isActive` *(boolean, optional)*: `true` (chỉ lấy đang kinh doanh), `false` (đã vô hiệu hóa), không truyền (lấy tất cả).
  - `page` *(int, optional, default: `0`)*: Trang số (bắt đầu từ 0).
  - `size` *(int, optional, default: `20`)*: Số phần tử mỗi trang.
  - `sort` *(string, optional, default: `name,asc`)*: Sắp xếp theo trường.

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
      },
      {
        "id": 2,
        "name": "Hành lá",
        "unit": "kg",
        "currentStock": 1.200,
        "minimumStock": 2.000,
        "isActive": true,
        "stockStatus": "LOW",
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

*Quy tắc tính trạng thái tồn kho `stockStatus` (Derived, không lưu DB):*
- `OUT_OF_STOCK`: Khi `currentStock <= 0`.
- `LOW`: Khi `currentStock > 0` và `currentStock <= minimumStock`.
- `NORMAL`: Các trường hợp còn lại.

---

### 3.2 Lấy Chi Tiết Nguyên Liệu (`GET /api/ingredients/{id}`)

- **Method:** `GET`
- **URL:** `/api/ingredients/{id}`
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

#### Phản hồi lỗi không tìm thấy (`404 Not Found`)
```json
{
  "success": false,
  "message": "Ingredient not found with id: 99",
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "details": null
  }
}
```

---

### 3.3 Tạo Mới Nguyên Liệu (`POST /api/ingredients`)

- **Method:** `POST`
- **URL:** `/api/ingredients`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Quy tắc nghiệp vụ:**
  - `currentStock` ban đầu luôn được khởi tạo mặc định bằng `0.000`.
  - Tồn kho chỉ được gia tăng qua phiếu nhập hàng hoặc điều chỉnh qua phiếu kiểm kê.
  - Tên nguyên liệu không được trùng lặp (không phân biệt hoa/thường).

#### Request Body
```json
{
  "name": "Giá đỗ",
  "unit": "kg",
  "minimumStock": 3.000
}
```

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "Ingredient created successfully",
  "data": {
    "id": 3,
    "name": "Giá đỗ",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 3.000,
    "isActive": true,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:10:00"
  }
}
```

#### Phản hồi lỗi trùng tên (`409 Conflict`)
```json
{
  "success": false,
  "message": "Ingredient with name 'Giá đỗ' already exists",
  "error": {
    "code": "DUPLICATE_INGREDIENT_NAME",
    "details": null
  }
}
```

---

### 3.4 Cập Nhật Thông Tin Nguyên Liệu (`PUT /api/ingredients/{id}`)

- **Method:** `PUT`
- **URL:** `/api/ingredients/{id}`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Quy tắc:** Chỉ cập nhật metadata (`name`, `unit`, `minimumStock`), bảo toàn số lượng tồn kho `currentStock`.

#### Request Body
```json
{
  "name": "Giá đỗ sạch",
  "unit": "kg",
  "minimumStock": 4.000
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Ingredient updated successfully",
  "data": {
    "id": 3,
    "name": "Giá đỗ sạch",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 4.000,
    "isActive": true,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:15:00"
  }
}
```

---

### 3.5 Vô Hiệu Hóa Nguyên Liệu (`PATCH /api/ingredients/{id}/deactivate`)

- **Method:** `PATCH`
- **URL:** `/api/ingredients/{id}/deactivate`
- **Vai trò được phép:** `OWNER`
- **Ràng buộc nghiệp vụ quan trọng:**
  - Nếu nguyên liệu đang nằm trong công thức của bất kỳ món ăn nào chưa ngừng kinh doanh (`menu_items.status <> 'DISCONTINUED'`), hệ thống sẽ chặn không cho vô hiệu hóa và trả về lỗi `409 Conflict`.
  - Chỉ cho phép deactivate nếu tất cả món chứa nó đã `DISCONTINUED` hoặc nguyên liệu không nằm trong công thức nào.

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Ingredient deactivated successfully",
  "data": {
    "id": 3,
    "name": "Giá đỗ sạch",
    "unit": "kg",
    "currentStock": 0.000,
    "minimumStock": 4.000,
    "isActive": false,
    "stockStatus": "OUT_OF_STOCK",
    "createdAt": "2026-10-14T00:10:00",
    "updatedAt": "2026-10-14T00:20:00"
  }
}
```

#### Phản hồi lỗi khi nguyên liệu đang dùng cho món đang bán (`409 Conflict`)
```json
{
  "success": false,
  "message": "Cannot deactivate ingredient because it is used in recipes of active menu items",
  "error": {
    "code": "INGREDIENT_IN_USE",
    "details": null
  }
}
```

---

## 4. API Kiểm Kê Kho (`/api/inventory-checks`) — BE-014

### 4.1 Tạo Phiếu Kiểm Kê Kho (`POST /api/inventory-checks`)

- **Method:** `POST`
- **URL:** `/api/inventory-checks`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Cơ chế xử lý (Atomic Transaction):**
  1. Khóa bi quan (`@Lock(PESSIMISTIC_WRITE)`) từng nguyên liệu trong danh sách.
  2. Lấy số lượng trên hệ thống `systemQuantity = ingredient.currentStock`.
  3. Tính chênh lệch `difference = actualQuantity - systemQuantity`.
  4. Cập nhật tồn kho thực tế: `ingredient.currentStock = actualQuantity`.
  5. Nếu `difference != 0`, tự động ghi 1 bản ghi `StockMovement` (type: `INVENTORY_ADJUSTMENT`, `quantityChange = difference`, `balanceAfter = actualQuantity`) kèm ghi chú lý do.

#### Request Body
```json
{
  "checkedAt": "2026-10-14T08:00:00",
  "note": "Kiểm kê định kỳ đầu tuần",
  "items": [
    {
      "ingredientId": 1,
      "actualQuantity": 14.000,
      "reason": "Hao hụt rã đông và lọc mỡ"
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
    "checkedAt": "2026-10-14T08:00:00",
    "note": "Kiểm kê định kỳ đầu tuần",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "systemQuantity": 15.500,
        "actualQuantity": 14.000,
        "difference": -1.500,
        "reason": "Hao hụt rã đông và lọc mỡ",
        "createdAt": "2026-10-14T08:00:00"
      }
    ],
    "createdAt": "2026-10-14T08:00:00",
    "updatedAt": "2026-10-14T08:00:00"
  }
}
```

---

### 4.2 Xem Danh Sách Phiếu Kiểm Kê (`GET /api/inventory-checks`)

- **Method:** `GET`
- **URL:** `/api/inventory-checks`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Query Parameters:** `page`, `size`, `sort` (mặc định: `checkedAt,desc`)

---

### 4.3 Xem Chi Tiết Phiếu Kiểm Kê (`GET /api/inventory-checks/{id}`)

- **Method:** `GET`
- **URL:** `/api/inventory-checks/{id}`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`

---

## 5. API Lịch Sử Biến Động Kho (`/api/stock-movements`) — BE-014

### 5.1 Tra Cứu Lịch Sử Biến Động Kho (`GET /api/stock-movements`)

Truy vấn audit trail lịch sử kho (chỉ đọc, bất biến - Append-only).

- **Method:** `GET`
- **URL:** `/api/stock-movements`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Query Parameters:**
  - `ingredientId` *(Long, optional)*: Lọc theo mã nguyên liệu.
  - `movementType` *(string, optional)*: `PURCHASE`, `ORDER_CONSUMPTION`, hoặc `INVENTORY_ADJUSTMENT`.
  - `page`, `size`, `sort` (mặc định: `occurredAt,desc`).

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "content": [
      {
        "id": 15,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "movementType": "INVENTORY_ADJUSTMENT",
        "quantityChange": -1.500,
        "balanceAfter": 14.000,
        "purchaseItemId": null,
        "orderItemId": null,
        "inventoryCheckItemId": 1,
        "note": "Hao hụt rã đông và lọc mỡ",
        "occurredAt": "2026-10-14T08:00:00"
      },
      {
        "id": 14,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "movementType": "PURCHASE",
        "quantityChange": 15.000,
        "balanceAfter": 15.500,
        "purchaseItemId": 1,
        "orderItemId": null,
        "inventoryCheckItemId": null,
        "note": "Nhập hàng theo phiếu #1",
        "occurredAt": "2026-10-14T06:30:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

---

## 6. API Quản Lý Phiếu Nhập Hàng (`/api/purchases`) — BE-015

### 6.1 Lấy Danh Sách Phiếu Nhập (`GET /api/purchases`)

- **Method:** `GET`
- **URL:** `/api/purchases`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Query Parameters:**
  - `fromDate` *(date, optional, format: `YYYY-MM-DD`)*: Ngày bắt đầu.
  - `toDate` *(date, optional, format: `YYYY-MM-DD`)*: Ngày kết thúc.
  - `page`, `size`, `sort` (mặc định: `purchaseDate,desc`).

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
        "note": "Nhập thịt bò tươi sáng sớm",
        "createdAt": "2026-10-14T06:30:00",
        "updatedAt": "2026-10-14T06:30:00"
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

- **Method:** `GET`
- **URL:** `/api/purchases/{id}`
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
    "note": "Nhập thịt bò tươi sáng sớm",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 15.000,
        "unitPrice": 250000.00,
        "subtotal": 3750000.00,
        "createdAt": "2026-10-14T06:30:00",
        "updatedAt": "2026-10-14T06:30:00"
      }
    ],
    "createdAt": "2026-10-14T06:30:00",
    "updatedAt": "2026-10-14T06:30:00"
  }
}
```

---

### 6.3 Tạo Phiếu Nhập Hàng (`POST /api/purchases`)

- **Method:** `POST`
- **URL:** `/api/purchases`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Cơ chế xử lý (Atomic Transaction):**
  1. Khóa bi quan (`PESSIMISTIC_WRITE`) từng nguyên liệu.
  2. Tính `subtotal = quantity * unitPrice`, cộng dồn vào `totalAmount`.
  3. Tăng tồn kho: `currentStock += quantity`.
  4. Ghi nhận `StockMovement` (type: `PURCHASE`, `quantityChange = quantity`, `balanceAfter = currentStock`).

#### Request Body
```json
{
  "purchaseDate": "2026-10-14",
  "note": "Nhập thịt bò tươi từ NCC Minh Thư",
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
    "note": "Nhập thịt bò tươi từ NCC Minh Thư",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 15.000,
        "unitPrice": 250000.00,
        "subtotal": 3750000.00,
        "createdAt": "2026-10-14T06:30:00",
        "updatedAt": "2026-10-14T06:30:00"
      }
    ],
    "createdAt": "2026-10-14T06:30:00",
    "updatedAt": "2026-10-14T06:30:00"
  }
}
```

---

### 6.4 Sửa Dòng Hàng Phiếu Nhập — Correction Logic (`PATCH /api/purchases/{purchaseId}/items/{itemId}`)

Cho phép điều chỉnh số lượng hoặc đơn giá của 1 dòng hàng đã nhập để sửa sai sót kế toán/kho (không cho xóa phiếu để bảo toàn vết kiểm toán).

- **Method:** `PATCH`
- **URL:** `/api/purchases/{purchaseId}/items/{itemId}`
- **Vai trò được phép:** `OWNER`, `INVENTORY_STAFF`
- **Quy trình xử lý bù trừ trong Transaction:**
  1. Kiểm tra dòng hàng `itemId` thuộc đúng phiếu `purchaseId`.
  2. Khóa bi quan nguyên liệu liên quan.
  3. Tính delta số lượng: $\Delta = \text{newQuantity} - \text{oldQuantity}$.
  4. Tính tồn kho mới: $\text{newStock} = \text{currentStock} + \Delta$.
  5. **Kiểm tra tồn kho âm:** Nếu $\text{newStock} < 0$, ném lỗi `409 Conflict: INSUFFICIENT_STOCK` (ví dụ: muốn giảm 5kg nhưng nhà bếp đã dùng hết chỉ còn 2kg trong kho).
  6. Cập nhật `quantity`, `unitPrice`, tính lại `subtotal` và tổng tiền phiếu `totalAmount`.
  7. Nếu $\Delta \neq 0$, tự động ghi thêm **1 bản ghi `StockMovement` bù trừ mới** (`quantityChange = delta`, `balanceAfter = newStock`) kèm ghi chú lý do (`reason`).

#### Request Body
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
    "note": "Nhập thịt bò tươi từ NCC Minh Thư",
    "items": [
      {
        "id": 1,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantity": 12.000,
        "unitPrice": 250000.00,
        "subtotal": 3000000.00,
        "createdAt": "2026-10-14T06:30:00",
        "updatedAt": "2026-10-14T07:15:00"
      }
    ],
    "createdAt": "2026-10-14T06:30:00",
    "updatedAt": "2026-10-14T07:15:00"
  }
}
```

#### Phản hồi lỗi không đủ tồn kho để giảm (`409 Conflict`)
```json
{
  "success": false,
  "message": "Cannot decrease purchase quantity. Current stock (1.500) is insufficient to deduct 3.000",
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "details": null
  }
}
```

---

## 7. API Quản Lý Công Thức Món Ăn — Recipe/BOM (`/api/menu-items/{menuItemId}/recipe`) — BE-033

> **Cơ chế Cache Invalidation:** Mọi thao tác thay đổi công thức (`PUT`, `POST`, `PATCH`, `DELETE`) đều tự động gửi lệnh xóa cache Redis thực đơn ngữ cảnh Gemini (`menu:context:v1`) qua `MenuContextCacheService.invalidateMenuContext()`.

---

### 7.1 Xem Công Thức Món Ăn (`GET /api/menu-items/{menuItemId}/recipe`)

- **Method:** `GET`
- **URL:** `/api/menu-items/{menuItemId}/recipe`
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

Xóa toàn bộ các dòng định lượng cũ của món và thay thế bằng danh sách mới.

- **Method:** `PUT`
- **URL:** `/api/menu-items/{menuItemId}/recipe`
- **Vai trò được phép:** `OWNER`
- **Ràng buộc:** Payload không được chứa trùng `ingredientId`, tất cả nguyên liệu phải tồn tại và đang hoạt động (`isActive == true`).

#### Request Body
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

- **Method:** `POST`
- **URL:** `/api/menu-items/{menuItemId}/recipe/items`
- **Vai trò được phép:** `OWNER`

#### Request Body
```json
{
  "ingredientId": 3,
  "quantityRequired": 0.080
}
```

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
      },
      {
        "id": 4,
        "ingredientId": 3,
        "ingredientName": "Giá đỗ sạch",
        "unit": "kg",
        "quantityRequired": 0.080
      }
    ]
  }
}
```

#### Phản hồi lỗi nếu nguyên liệu đã có sẵn trong công thức (`409 Conflict`)
```json
{
  "success": false,
  "message": "Ingredient already exists in recipe for this menu item",
  "error": {
    "code": "DUPLICATE_RECIPE_ITEM",
    "details": null
  }
}
```

---

### 7.4 Cập Nhật Định Lượng 1 Nguyên Liệu (`PATCH /api/menu-items/{menuItemId}/recipe/items/{ingredientId}`)

- **Method:** `PATCH`
- **URL:** `/api/menu-items/{menuItemId}/recipe/items/{ingredientId}`
- **Vai trò được phép:** `OWNER`

#### Request Body
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
        "id": 2,
        "ingredientId": 1,
        "ingredientName": "Thịt bò tái",
        "unit": "kg",
        "quantityRequired": 0.200
      },
      {
        "id": 3,
        "ingredientId": 2,
        "ingredientName": "Hành lá",
        "unit": "kg",
        "quantityRequired": 0.050
      },
      {
        "id": 4,
        "ingredientId": 3,
        "ingredientName": "Giá đỗ sạch",
        "unit": "kg",
        "quantityRequired": 0.080
      }
    ]
  }
}
```

---

### 7.5 Xóa 1 Nguyên Liệu Khỏi Công Thức (`DELETE /api/menu-items/{menuItemId}/recipe/items/{ingredientId}`)

- **Method:** `DELETE`
- **URL:** `/api/menu-items/{menuItemId}/recipe/items/{ingredientId}`
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
  -d '{"username":"owner","password":"Password@123"}'
```

### 8.2 Xem danh sách nguyên liệu (kèm cảnh báo tồn kho)
```bash
curl -X GET "http://localhost:8080/api/ingredients?page=0&size=20" \
  -H "Authorization: Bearer <access_token>"
```

### 8.3 Tạo phiếu nhập hàng
```bash
curl -X POST http://localhost:8080/api/purchases \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "purchaseDate": "2026-10-14",
    "note": "Nhập thịt bò tươi",
    "items": [
      {
        "ingredientId": 1,
        "quantity": 10.0,
        "unitPrice": 250000.0
      }
    ]
  }'
```

### 8.4 Điều chỉnh số lượng dòng hàng phiếu nhập (bù trừ kho)
```bash
curl -X PATCH http://localhost:8080/api/purchases/1/items/1 \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 8.5,
    "unitPrice": 250000.0,
    "reason": "Cân lại thực tế hao hụt 1.5kg"
  }'
```

### 8.5 Kiểm tra lịch sử biến động kho sau điều chỉnh
```bash
curl -X GET "http://localhost:8080/api/stock-movements?ingredientId=1" \
  -H "Authorization: Bearer <access_token>"
```

### 8.6 Xem công thức món ăn Phở Bò
```bash
curl -X GET http://localhost:8080/api/menu-items/1/recipe \
  -H "Authorization: Bearer <access_token>"
```
