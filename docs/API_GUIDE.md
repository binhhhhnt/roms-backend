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

## 5. Mẫu Gọi API Bằng cURL

### 5.1 Đăng nhập (Lưu Cookie tự động ra file `cookies.txt`)
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -c cookies.txt \
  -d '{"username":"owner","password":"Password@123"}'
```

### 5.2 Xem Profile hiện tại
```bash
curl -X GET http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <access_token_nhận_được>"
```

### 5.3 Làm mới Access Token (Gửi kèm Cookie từ file)
```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -b cookies.txt \
  -c cookies.txt
```

### 5.4 Đăng xuất
```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <access_token_nhận_được>" \
  -b cookies.txt
```

## 6. API Đọc Danh Mục (BE-007)

Hợp đồng đọc đã được duyệt: mọi vai trò đã xác thực được đọc. `TABLE_ACCOUNT` chỉ thấy danh mục có `isActive=true`; truy cập chi tiết danh mục inactive trả `404`, giống danh mục không tồn tại. Các vai trò staff và Owner được đọc cả danh mục inactive. Không yêu cầu service session cho hai API đọc này.

- `GET /api/categories`: trả `ApiResponse<PageResponse<CategoryResponse>>`.
- `GET /api/categories/{id}`: trả `ApiResponse<CategoryResponse>`.
- Header: `Authorization: Bearer <accessToken>`.
- DTO: `id`, `name`, `description` (có thể null), `displayOrder`, `isActive`.
- Danh sách nhận `page` (bắt đầu từ 0), `size`, `sort`; mặc định page 0, size 20, sort `displayOrder,asc`. Kích thước trang tối đa 100; yêu cầu lớn hơn được giới hạn xuống 100.
- Sort hỗ trợ `id`, `name`, `description`, `displayOrder`, `isActive`; ví dụ `sort=name,desc`. Tự thêm `id,asc` nếu chưa có sort theo id để bảo đảm thứ tự ổn định.
- Lọc danh mục inactive của tablet được thực hiện trước phân trang; `totalElements` chỉ tính danh mục được phép đọc.
- Thành công: `200`. Chưa xác thực: `401`. ID không đúng kiểu/không dương hoặc sort field không hỗ trợ: `400`. Không tìm thấy hoặc tablet đọc inactive: `404`. Lỗi theo envelope chung.

Ví dụ danh sách:

```bash
curl "http://localhost:8080/api/categories?page=0&size=20&sort=displayOrder,asc" \
  -H "Authorization: Bearer <accessToken>"
```

```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": {
    "content": [
      { "id": 1, "name": "Món Chính", "description": null, "displayOrder": 1, "isActive": true }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Chi tiết trả cùng DTO trực tiếp trong `data`, với message `Category retrieved successfully`.

## 7. API Ghi Catalog và Ảnh Cloudinary (BE-007)

Hợp đồng ghi đã được duyệt: chỉ `OWNER` được tạo/cập nhật danh mục, món và upload/xóa ảnh. Dùng Bearer JWT hiện có; thiếu xác thực trả `401`, vai trò khác trả `403`. Không có API xóa cứng category/menu. Ngừng hoạt động category bằng `isActive=false`; ngừng bán món qua BE-008 của BINH-DEV.

| Method | Endpoint | Thành công |
|---|---|---|
| POST | `/api/categories` | 201, Location và CategoryResponse |
| PUT | `/api/categories/{id}` | 200, CategoryResponse |
| POST | `/api/menu-items` | 201, Location và MenuItemResponse |
| PUT | `/api/menu-items/{id}` | 200, MenuItemResponse |
| POST | `/api/menu-items/{menuItemId}/images` | 201, Location và MenuItemImageResponse |
| DELETE | `/api/menu-items/{menuItemId}/images/{imageId}` | 204, body rỗng |

Response JSON dùng `ApiResponse<T>`. Request không hợp lệ trả `400`, resource không tồn tại trả `404`; lỗi provider/DB dùng lỗi `500` chung không tiết lộ chi tiết nội bộ.

### 7.1 Category Write Request

POST và PUT dùng cùng payload; PUT là cập nhật đầy đủ các trường được phép:

```json
{"name":"Món Chính","description":"Món ăn chính","displayOrder":1,"isActive":true}
```

`name` bắt buộc, không trắng, tối đa 100 ký tự; `description` nullable, tối đa 255 ký tự; `displayOrder` và `isActive` bắt buộc. Không áp đặt unique tên vì schema không quy định. Tên được trim. Category có thể ngừng hoạt động mà vẫn giữ các món/lịch sử liên quan.

### 7.2 Menu Item Write Request

```json
{"categoryId":1,"name":"Phở Bò","description":"Phở bò truyền thống","price":65000.00,"isFeatured":true}
```

`categoryId` dương, bắt buộc và phải tồn tại; `name` không trắng, tối đa 255 ký tự; `description` nullable; `price` bắt buộc, không âm, tối đa 10 chữ số phần nguyên và 2 chữ số thập phân; `isFeatured` bắt buộc. Tên được trim. Tạo món mặc định `AVAILABLE`; PUT giữ nguyên status. Payload không có trường ghi status, recipe, stock, available_quantity hay audit.

MenuItemResponse gồm `id`, `categoryId`, `name`, `description`, `price`, `isFeatured`, `status`. Sửa giá món không cập nhật snapshot giá của OrderItem.

### 7.3 Ảnh Menu

Upload multipart có part `file` và query/form parameter `displayOrder` (mặc định 0). Chấp nhận JPEG/PNG/WebP tối đa 5 MB mỗi file; kiểm tra content type và chữ ký file trước khi gửi provider, Cloudinary xử lý/kiểm tra ảnh thực tế. Giới hạn multipart request là 6 MB.

```bash
curl -X POST http://localhost:8080/api/menu-items/1/images \
  -H "Authorization: Bearer <accessToken>" \
  -F "file=@food.png;type=image/png" -F "displayOrder=0"
```

MenuItemImageResponse gồm `id`, `menuItemId`, `imageUrl`, `displayOrder`. PostgreSQL chỉ lưu URL và metadata theo schema hiện có.

Cloudinary SDK `cloudinary-http5:2.3.0` dùng public_id `roms/menu-items/{menuItemId}/images/{imageId}` cho ảnh được tạo bởi API này, không thêm cột schema. Credentials lấy từ `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`; không cấu hình sẽ trả lỗi khi gọi chức năng ảnh, không yêu cầu credentials để dùng API catalog. Hướng dẫn SDK: https://cloudinary.com/documentation/java_integration.

Upload chạy trong transaction; metadata tạm chưa commit dùng để lấy ID. Khi rollback, thử xóa asset bù; cleanup thất bại chỉ ghi log asset ID, có thể còn ảnh orphan cần xử lý vận hành. Xóa ảnh kiểm tra đúng menu, khóa ghi theo menu, xóa asset trước metadata; provider lỗi thì giữ metadata để thử lại. Nếu provider đã xóa nhưng DB rollback, metadata có thể tạm trỏ tới ảnh đã mất; lần xóa lại chấp nhận provider `not found`. Không có transaction phân tán hoặc bảo đảm cleanup khi process bị dừng đột ngột. Ảnh legacy không theo public_id này cần được đối chiếu trước khi dùng API xóa.

Thay đổi catalog và ảnh đăng ký invalidate menu context sau commit; rollback không invalidate. Tái sử dụng cache primitive hiện có; lỗi Redis invalidation vẫn tuân theo cơ chế ghi log/TTL hiện tại, chưa có retry bền vững.

### 7.4 Phạm Vi Chưa Hoàn Tất

GET menu list/detail cho tablet và tích hợp live sellability đang chờ mã BE-008 do người dùng cung cấp. Không tự triển khai thuật toán recipe/stock hoặc API status thuộc BINH-DEV. Các endpoint GET menu chưa được công bố trong checkout này.

Đã kiểm tra biên dịch và 40 test BE-007 (service, mapper qua workflow, API standalone, Cloudinary mock); suite không kết nối DB có 109 test pass. Chưa chạy PostgreSQL/Flyway integration, chuỗi filter JWT thực tế cho các endpoint mới hoặc Cloudinary smoke test. Dùng Surefire 3.2.5 trực tiếp để tránh cache mặc định 3.5.6 thiếu thành phần; không thay cấu hình runner trong pom.
