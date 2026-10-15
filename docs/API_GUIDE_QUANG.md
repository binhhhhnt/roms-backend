# Hướng Dẫn Sử Dụng API ROMS Backend — QUANG (Phase 1 & Modules Phụ Trách)

Tài liệu hướng dẫn chi tiết cách thức gọi, cấu trúc JSON Request/Response, các quy tắc nghiệp vụ (Business Rules), mã lỗi và kịch bản kiểm thử mẫu bằng cURL cho toàn bộ các API do **QUANG** phụ trách và đã hoàn thiện trong **Phase 1** theo phân công tại [docs/TASKS.md](file:///c:/Users/Quang/.gemini/antigravity/scratch/roms-backend/docs/TASKS.md).

---

## 1. Thông Tin Chung & Quy Chuẩn API

- **Base URL:** `http://localhost:8080` (hoặc cấu hình cổng qua biến môi trường `SERVER_PORT`)
- **Định dạng dữ liệu:** `application/json` (Encoding: `UTF-8`)
- **Phân lớp kiến trúc:** Tuân thủ chuẩn 3-Layer Architecture (Package by Layer: Controller → Service → Repository).
- **Chuẩn cấu trúc Envelope Response:**

### 1.1 Chuẩn Response Thành Công (`200 OK`, `201 Created`)
```json
{
  "success": true,
  "message": "Thông báo kết quả thao tác",
  "data": { ... }
}
```

### 1.2 Chuẩn Response Phân Trang (`PageResponse<T>`)
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3
  }
}
```

### 1.3 Chuẩn Response Lỗi (`400`, `401`, `403`, `404`, `409`, `500`)
```json
{
  "success": false,
  "message": "Mô tả nguyên nhân lỗi cho người dùng",
  "error": {
    "code": "MÃ_LỖI_NGHIỆP_VỤ",
    "details": null
  }
}
```

---

## 2. Danh Mục Các Task & API Do QUANG Phụ Trách

| Mã Task | Tên Nghiệp Vụ | Đường Dẫn API | Phương Thức | Quyền Truy Cập | Trạng Thái |
|---|---|---|:---:|:---:|:---:|
| **BE-005** | Danh sách người dùng (lọc & phân trang) | `/api/users` | `GET` | `OWNER` | **Hoàn thành** |
| **BE-005** | Chi tiết người dùng | `/api/users/{id}` | `GET` | `OWNER` | **Hoàn thành** |
| **BE-005** | Tạo người dùng mới | `/api/users` | `POST` | `OWNER` | **Hoàn thành** |
| **BE-005** | Cập nhật người dùng | `/api/users/{id}` | `PUT` | `OWNER` | **Hoàn thành** |
| **BE-005** | Bật/tắt trạng thái người dùng (active/inactive) | `/api/users/{id}/status` | `PATCH` | `OWNER` | **Hoàn thành** |
| **ROMS-004** | Xem thông tin hồ sơ cá nhân | `/api/users/me/profile` | `GET` | Đã đăng nhập | **Hoàn thành** |
| **ROMS-005** | Cập nhật hồ sơ cá nhân | `/api/users/me/profile` | `PUT` | Đã đăng nhập | **Hoàn thành** |
| **ROMS-006** | Đổi mật khẩu cá nhân | `/api/users/me/change-password` | `POST` | Đã đăng nhập | **Hoàn thành** |
| **BE-018** | Yêu cầu gửi OTP quên mật khẩu | `/api/auth/forgot-password` | `POST` | Public | **Hoàn thành** |
| **BE-019** | Kiểm tra mã OTP | `/api/auth/verify-otp` | `POST` | Public | **Hoàn thành** |
| **BE-019** | Đặt lại mật khẩu với OTP | `/api/auth/reset-password` | `POST` | Public | **Hoàn thành** |

---

## 3. Chi Tiết API Quản Lý Tài Khoản Người Dùng (`/api/users` — BE-005)

Tất cả các API trong mục này yêu cầu đăng nhập với tài khoản có quyền **`OWNER`** (Quản trị viên).

---

### 3.1 Lấy Danh Sách Người Dùng (`GET /api/users`)

Hỗ trợ phân trang, sắp xếp và lọc theo vai trò, trạng thái kích hoạt hoặc tìm kiếm theo từ khóa.

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/users`
- **Headers:** `Authorization: Bearer <accessToken>`
- **Query Parameters (Tùy chọn):**
  - `role`: `OWNER` | `SERVICE_STAFF` | `KITCHEN_STAFF` | `INVENTORY_STAFF` | `TABLE_ACCOUNT`
  - `isActive`: `true` | `false`
  - `search`: Từ khóa tìm kiếm (so khớp không phân biệt hoa thường theo `username`, `fullName` hoặc `email`)
  - `page`: Chỉ số trang, bắt đầu từ 0 (Mặc định: `0`)
  - `size`: Số bản ghi trên mỗi trang (Mặc định: `10`)
  - `sort`: Trường sắp xếp theo định dạng `field,direction` (Mặc định: `id,desc`)

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Users retrieved successfully",
  "data": {
    "content": [
      {
        "id": 1,
        "username": "owner",
        "fullName": "Nguyễn Văn Chủ",
        "email": "owner@roms.com",
        "role": "OWNER",
        "tableId": null,
        "tableCode": null,
        "isActive": true,
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      },
      {
        "id": 6,
        "username": "table_t01",
        "fullName": "Tablet Bàn T01",
        "email": null,
        "role": "TABLE_ACCOUNT",
        "tableId": 1,
        "tableCode": "T01",
        "isActive": true,
        "createdAt": "2026-10-14T00:00:00",
        "updatedAt": "2026-10-14T00:00:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

---

### 3.2 Xem Chi Tiết Một Người Dùng (`GET /api/users/{id}`)

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/users/{id}`
- **Headers:** `Authorization: Bearer <accessToken>`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "User retrieved successfully",
  "data": {
    "id": 2,
    "username": "service01",
    "fullName": "Trần Thị Thu Ngân",
    "email": "service01@roms.com",
    "role": "SERVICE_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": true,
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

#### Response Lỗi Thường Gặp
- **Không tìm thấy người dùng (`404 Not Found`):**
  ```json
  {
    "success": false,
    "message": "User not found with id: 999",
    "error": {
      "code": "RESOURCE_NOT_FOUND",
      "details": null
    }
  }
  ```

---

### 3.3 Tạo Tài Khoản Người Dùng Mới (`POST /api/users`)

Tạo tài khoản mới với 1 trong 5 vai trò.

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/users`
- **Headers:** 
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

#### Quy tắc nghiệp vụ (Business Rules):
1. `username`: Bắt buộc, duy nhất trong hệ thống, từ 3 đến 50 ký tự.
2. `password`: Bắt buộc, tối thiểu 6 ký tự.
3. `fullName`: Bắt buộc, tối đa 255 ký tự.
4. `role`: Bắt buộc (`OWNER`, `SERVICE_STAFF`, `KITCHEN_STAFF`, `INVENTORY_STAFF`, `TABLE_ACCOUNT`).
5. `tableId`:
   - Nếu `role == TABLE_ACCOUNT`: **Bắt buộc** phải cung cấp `tableId` hợp lệ, và bàn đó **chưa được gắn** cho bất kỳ tài khoản tablet nào khác.
   - Nếu `role` là nhân viên (`OWNER`, `SERVICE_STAFF`, v.v.): `tableId` **phải là null** (nếu gửi lên sẽ bị báo lỗi nghiệp vụ).

#### Request Body — Tạo Nhân Viên Bếp
```json
{
  "username": "chef_lan",
  "password": "Password@123",
  "fullName": "Lê Văn Lan",
  "email": "lan.chef@roms.com",
  "role": "KITCHEN_STAFF",
  "tableId": null
}
```

#### Request Body — Tạo Tablet Gắn Cho Bàn Số 3
```json
{
  "username": "table_t03_new",
  "password": "Password@123",
  "fullName": "Tablet Bàn T03",
  "role": "TABLE_ACCOUNT",
  "tableId": 3
}
```

#### Response Thành Công (`201 Created`)
```json
{
  "success": true,
  "message": "User created successfully",
  "data": {
    "id": 15,
    "username": "chef_lan",
    "fullName": "Lê Văn Lan",
    "email": "lan.chef@roms.com",
    "role": "KITCHEN_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": true,
    "createdAt": "2026-10-14T07:30:00",
    "updatedAt": "2026-10-14T07:30:00"
  }
}
```

#### Response Lỗi Nghiệp Vụ
- **Tên đăng nhập đã tồn tại (`400 Bad Request`):**
  ```json
  {
    "success": false,
    "message": "Username already exists: chef_lan",
    "error": {
      "code": "USERNAME_ALREADY_EXISTS",
      "details": null
    }
  }
  ```
- **Nhân viên nhưng gửi kèm tableId (`400 Bad Request`):**
  ```json
  {
    "success": false,
    "message": "Staff account must not be assigned to a table",
    "error": {
      "code": "STAFF_CANNOT_HAVE_TABLE",
      "details": null
    }
  }
  ```
- **Tài khoản tablet nhưng thiếu bàn (`400 Bad Request`):**
  ```json
  {
    "success": false,
    "message": "Table ID is required for TABLE_ACCOUNT",
    "error": {
      "code": "TABLE_REQUIRED_FOR_TABLE_ACCOUNT",
      "details": null
    }
  }
  ```
- **Bàn đã được gắn cho tài khoản tablet khác (`400 Bad Request`):**
  ```json
  {
    "success": false,
    "message": "Table is already assigned to another account: 3",
    "error": {
      "code": "TABLE_ALREADY_ASSIGNED",
      "details": null
    }
  }
  ```

---

### 3.4 Cập Nhật Thông Tin Người Dùng (`PUT /api/users/{id}`)

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/users/{id}`
- **Headers:**
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

#### Request Body
```json
{
  "fullName": "Lê Văn Lan (Bếp Trưởng)",
  "email": "lan.cheftruong@roms.com",
  "role": "KITCHEN_STAFF",
  "tableId": null
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "User updated successfully",
  "data": {
    "id": 15,
    "username": "chef_lan",
    "fullName": "Lê Văn Lan (Bếp Trưởng)",
    "email": "lan.cheftruong@roms.com",
    "role": "KITCHEN_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": true,
    "createdAt": "2026-10-14T07:30:00",
    "updatedAt": "2026-10-14T07:45:00"
  }
}
```

---

### 3.5 Bật/Tắt Trạng Thái Hoạt Động Của Người Dùng (`PATCH /api/users/{id}/status`)

- **Method:** `PATCH`
- **URL:** `http://localhost:8080/api/users/{id}/status`
- **Headers:**
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

#### Đặc tính bảo mật cốt lõi:
- **Tự động thu hồi phiên (Session Revocation):** Khi một tài khoản bị vô hiệu hóa (`isActive: false`), hệ thống sẽ **ngay lập tức thu hồi toàn bộ Refresh Token** đang còn hiệu lực của tài khoản đó trong cơ sở dữ liệu. Người dùng/tablet đó sẽ bị chặn ngay ở lần làm mới token hoặc gửi STOMP frame tiếp theo.
- **Bảo vệ tài khoản OWNER đang thao tác:** Không thể tự vô hiệu hóa chính tài khoản của mình.

#### Request Body
```json
{
  "isActive": false
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "User status updated successfully",
  "data": {
    "id": 15,
    "username": "chef_lan",
    "fullName": "Lê Văn Lan (Bếp Trưởng)",
    "email": "lan.cheftruong@roms.com",
    "role": "KITCHEN_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": false,
    "createdAt": "2026-10-14T07:30:00",
    "updatedAt": "2026-10-14T07:50:00"
  }
}
```

#### Response Lỗi Khi Tự Vô Hiệu Hóa Chính Mình (`400 Bad Request`)
```json
{
  "success": false,
  "message": "Cannot deactivate your own account",
  "error": {
    "code": "CANNOT_DEACTIVATE_OWN_ACCOUNT",
    "details": null
  }
}
```

---

## 4. Chi Tiết API Thông Tin Cá Nhân & Đổi Mật Khẩu (`/api/users/me` — ROMS-004–006)

Tất cả các API này áp dụng cho người dùng đã đăng nhập (bất kể vai trò nào).

---

### 4.1 Xem Thông Tin Hồ Sơ Cá Nhân (`GET /api/users/me/profile`)

- **Method:** `GET`
- **URL:** `http://localhost:8080/api/users/me/profile`
- **Headers:** `Authorization: Bearer <accessToken>`

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": 2,
    "username": "service01",
    "fullName": "Trần Thị Thu Ngân",
    "email": "service01@roms.com",
    "role": "SERVICE_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": true,
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T00:00:00"
  }
}
```

---

### 4.2 Cập Nhật Hồ Sơ Cá Nhân (`PUT /api/users/me/profile`)

Cho phép người dùng tự đổi họ tên và email hiển thị.

- **Method:** `PUT`
- **URL:** `http://localhost:8080/api/users/me/profile`
- **Headers:**
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

#### Request Body
```json
{
  "fullName": "Trần Thu Ngân (Ca Trưởng)",
  "email": "ngan.tran@roms.com"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "User profile updated successfully",
  "data": {
    "id": 2,
    "username": "service01",
    "fullName": "Trần Thu Ngân (Ca Trưởng)",
    "email": "ngan.tran@roms.com",
    "role": "SERVICE_STAFF",
    "tableId": null,
    "tableCode": null,
    "isActive": true,
    "createdAt": "2026-10-14T00:00:00",
    "updatedAt": "2026-10-14T07:55:00"
  }
}
```

---

### 4.3 Đổi Mật Khẩu Cá Nhân (`POST /api/users/me/change-password`)

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/users/me/change-password`
- **Headers:**
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`

#### Đặc tính bảo mật:
- Kiểm tra tính khớp của mật khẩu hiện tại (`currentPassword`).
- Mật khẩu mới (`newPassword`) phải từ 6 ký tự trở lên.
- **Tự động thu hồi phiên:** Ngay sau khi đổi mật khẩu thành công, toàn bộ các phiên Refresh Token đang mở trên mọi thiết bị sẽ bị thu hồi. Người dùng cần đăng nhập lại với mật khẩu mới.

#### Request Body
```json
{
  "currentPassword": "Password@123",
  "newPassword": "NewPassword@456"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": null
}
```

#### Response Lỗi Khi Sai Mật Khẩu Hiện Tại (`400 Bad Request`)
```json
{
  "success": false,
  "message": "Invalid current password",
  "error": {
    "code": "INVALID_OLD_PASSWORD",
    "details": null
  }
}
```

---

## 5. Chi Tiết API Quên & Đặt Lại Mật Khẩu OTP (`/api/auth` — BE-018 & BE-019)

Hệ thống sử dụng cơ chế xác thực OTP lưu trữ trên **Redis**:
- **Độ dài mã OTP:** 6 chữ số ngẫu nhiên (`100000` – `999999`).
- **Bảo mật:** Mã OTP được hash bằng **SHA-256** trước khi lưu vào Redis.
- **Thời hạn (TTL):** 5 phút.
- **Giới hạn số lần thử (Brute-force protection):** Tối đa **5 lần nhập sai**. Nếu vượt quá 5 lần, mã OTP sẽ bị vô hiệu hóa tức thì.

---

### 5.1 Yêu Cầu Gửi Mã OTP Quên Mật Khẩu (`POST /api/auth/forgot-password`)

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/forgot-password`
- **Yêu cầu xác thực:** Public (Không cần Token)
- **Headers:** `Content-Type: application/json`

#### Request Body
```json
{
  "email": "owner@roms.com"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "If the email is registered, a password reset OTP has been sent.",
  "data": null
}
```
> **Lưu ý bảo mật:** Để tránh lộ danh sách email người dùng (User Enumeration Attack), hệ thống luôn trả về thông báo 200 thành công ngay cả khi email không tồn tại trong hệ thống. Nếu email hợp lệ, mã OTP sẽ được phát sinh và lưu vào Redis.

---

### 5.2 Kiểm Tra Mã OTP (`POST /api/auth/verify-otp`)

Cho phép ứng dụng Client/Frontend kiểm tra xem mã OTP người dùng nhập vào có chính xác không trước khi chuyển sang bước đặt mật khẩu mới.

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/verify-otp`
- **Yêu cầu xác thực:** Public
- **Headers:** `Content-Type: application/json`

#### Request Body
```json
{
  "email": "owner@roms.com",
  "otp": "482910"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "OTP is valid",
  "data": null
}
```

#### Response Lỗi Nhập Sai OTP (`400 Bad Request`)
```json
{
  "success": false,
  "message": "Invalid OTP. 4 attempt(s) remaining.",
  "error": {
    "code": "INVALID_OTP",
    "details": null
  }
}
```

#### Response Lỗi Vượt Quá 5 Lần Thử (`400 Bad Request`)
```json
{
  "success": false,
  "message": "Maximum OTP verification attempts exceeded. Please request a new OTP.",
  "error": {
    "code": "OTP_MAX_ATTEMPTS_EXCEEDED",
    "details": null
  }
}
```

#### Response Lỗi OTP Hết Hạn Hoặc Không Tồn Tại (`400 Bad Request`)
```json
{
  "success": false,
  "message": "OTP has expired or does not exist",
  "error": {
    "code": "OTP_EXPIRED_OR_NOT_FOUND",
    "details": null
  }
}
```

---

### 5.3 Đặt Lại Mật Khẩu Mới Bằng OTP (`POST /api/auth/reset-password`)

- **Method:** `POST`
- **URL:** `http://localhost:8080/api/auth/reset-password`
- **Yêu cầu xác thực:** Public
- **Headers:** `Content-Type: application/json`

#### Quy trình xử lý:
1. Xác minh mã OTP trong Redis.
2. Mã hóa mật khẩu mới bằng BCrypt và lưu vào DB.
3. Xóa mã OTP khỏi Redis.
4. **Thu hồi toàn bộ Refresh Token / phiên đăng nhập cũ** của tài khoản để đảm bảo tài khoản an toàn tuyệt đối.

#### Request Body
```json
{
  "email": "owner@roms.com",
  "otp": "482910",
  "newPassword": "NewSecurePassword@123"
}
```

#### Response Thành Công (`200 OK`)
```json
{
  "success": true,
  "message": "Password has been reset successfully. Please login with your new password.",
  "data": null
}
```

---

## 6. Kịch Bản Kiểm Thử Hoàn Chỉnh Bằng cURL (Scenario Testing)

### Kịch Bản 1: OWNER Quản Lý Tài Khoản Nhân Viên
```bash
# 1. Đăng nhập với quyền OWNER để lấy Access Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"owner","password":"Password@123"}'

# (Giả sử TOKEN="eyJhbGciOi...")

# 2. Lấy danh sách nhân viên phục vụ đang hoạt động
curl -X GET "http://localhost:8080/api/users?role=SERVICE_STAFF&isActive=true&page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"

# 3. Tạo một nhân viên bếp mới
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "chef_test",
    "password": "Password@123",
    "fullName": "Nguyễn Văn Bếp Mới",
    "email": "cheftest@roms.com",
    "role": "KITCHEN_STAFF",
    "tableId": null
  }'

# 4. Vô hiệu hóa tài khoản vừa tạo (ID: 16)
curl -X PATCH http://localhost:8080/api/users/16/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"isActive": false}'
```

---

### Kịch Bản 2: Nhân Viên Xem Profile & Tự Đổi Mật Khẩu
```bash
# 1. Nhân viên đăng nhập
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"service01","password":"Password@123"}'

# 2. Xem Profile cá nhân
curl -X GET http://localhost:8080/api/users/me/profile \
  -H "Authorization: Bearer $STAFF_TOKEN"

# 3. Cập nhật họ tên
curl -X PUT http://localhost:8080/api/users/me/profile \
  -H "Authorization: Bearer $STAFF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fullName": "Trần Thị Thu Ngân (Cập nhật)", "email": "ngan.updated@roms.com"}'

# 4. Đổi mật khẩu cá nhân
curl -X POST http://localhost:8080/api/users/me/change-password \
  -H "Authorization: Bearer $STAFF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword": "Password@123", "newPassword": "NewPassword@789"}'
```

---

### Kịch Bản 3: Quên & Đặt Lại Mật Khẩu Bằng OTP (Public)
```bash
# 1. Yêu cầu mã OTP về email
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"service01@roms.com"}'

# 2. Kiểm tra tính hợp lệ của mã OTP (ví dụ OTP nhận được là 123456)
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"email":"service01@roms.com","otp":"123456"}'

# 3. Đặt lại mật khẩu mới
curl -X POST http://localhost:8080/api/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "email":"service01@roms.com",
    "otp":"123456",
    "newPassword":"SuperSafePassword@2026"
  }'

# 4. Đăng nhập lại với mật khẩu mới để kiểm tra
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"service01","password":"SuperSafePassword@2026"}'
```

---

## 7. Bảng Tổng Hợp Mã Lỗi Nghiệp Vụ (Business Error Codes)

| Mã Lỗi | HTTP Status | Ý Nghĩa / Tình Huống Xảy Ra |
|---|:---:|---|
| `RESOURCE_NOT_FOUND` | `404` | Không tìm thấy User hoặc Table theo ID yêu cầu |
| `USERNAME_ALREADY_EXISTS` | `400` | Tên đăng nhập đã có người sử dụng |
| `STAFF_CANNOT_HAVE_TABLE` | `400` | Gán `tableId` cho tài khoản nhân viên (không phải `TABLE_ACCOUNT`) |
| `TABLE_REQUIRED_FOR_TABLE_ACCOUNT` | `400` | Tài khoản tablet `TABLE_ACCOUNT` nhưng không chỉ định `tableId` |
| `TABLE_ALREADY_ASSIGNED` | `400` | Bàn chỉ định đã có tài khoản tablet khác gắn kết |
| `CANNOT_DEACTIVATE_OWN_ACCOUNT` | `400` | Quản trị viên cố gắng tự vô hiệu hóa tài khoản của chính mình |
| `INVALID_OLD_PASSWORD` | `400` | Mật khẩu hiện tại không khớp khi gọi đổi mật khẩu |
| `OTP_EXPIRED_OR_NOT_FOUND` | `400` | Mã OTP đã quá 5 phút hoặc chưa từng được gửi |
| `INVALID_OTP` | `400` | Mã OTP nhập vào không trùng khớp (kèm số lần thử còn lại) |
| `OTP_MAX_ATTEMPTS_EXCEEDED` | `400` | Nhập sai OTP quá 5 lần; OTP bị hủy để bảo mật |
| `VALIDATION_ERROR` | `400` | Dữ liệu đầu vào vi phạm Bean Validation (`@NotBlank`, `@Size`, `@Email`...) |
| `UNAUTHORIZED` | `401` | Token không hợp lệ, phiên hết hạn hoặc bị thu hồi |
| `ACCESS_DENIED` | `403` | Không đủ quyền thực thi (ví dụ: nhân viên gọi API của `OWNER`) |

---

## 8. Lộ Trình Triển Khai Tiếp Theo Của QUANG

1. **Phase 2 — Quản lý Bàn & Phiên Phục Vụ:**
   - **BE-009:** Quản lý bàn ăn (`RestaurantTable`) & Vòng đời phiên phục vụ (`ServiceSession`): Mở phiên, đóng phiên, kiểm tra trạng thái đơn hàng trước khi đóng, lịch sử phục vụ (`ROMS-011–015, 017–018, 024, 036`).
   - **BE-012:** Chuyển bàn (`table transfer`) và lưu vết lịch sử chuyển bàn (`table_transfer_history` - `ROMS-016`).
   - Phối hợp với STOMP Event Publisher phát thông báo cập nhật trạng thái bàn sau commit.
2. **Phase 3 — Tích Hợp Trợ Lý Ảo Gemini AI:**
   - **BE-022, BE-023, BE-024:** Menu Context Caching vào Redis, gợi ý món ăn theo công thức và nguyên liệu khả dụng (`ROMS-032`).
