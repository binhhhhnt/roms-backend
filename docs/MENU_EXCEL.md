# Menu Excel (Owner)

Trên `/owner/categories`, nút **Chọn file Excel** có icon tải lên, nằm bên phải **Thêm danh mục** và mở trang riêng `/owner/categories/import`. Trang mới có nút **Quay lại** ở góc trái (giữ trang danh mục trước đó), **File Excel mẫu** ở góc phải mở trực tiếp cửa sổ xem trước, và vùng chọn file ở giữa. Danh sách danh mục không được render trên trang nhập Excel. Nút tải file mẫu nằm trong cửa sổ xem trước; hai tab sheet nằm dưới bảng. Nội dung xem trước và file tải xuống dùng chung nguồn dữ liệu mẫu (6 danh mục, 24 món ăn).

## API

Các endpoint yêu cầu đăng nhập với vai trò `OWNER`.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/api/menu-excel/template` | File tải xuống `Menu_mau.xlsx` |
| GET | `/api/menu-excel/template/preview` | `ApiResponse<MenuExcelPreview>` gồm filename và hai sheet (name, columns, rows) |
| POST | `/api/menu-excel/import` | Multipart field `file`; trả về categoriesCreated, categoriesReused, itemsCreated, itemsSkipped |

## Cấu trúc

File phải có đúng hai sheet, tên và thứ tự cột như sau:

- **Danh mục**: STT, Tên danh mục, Mô tả.
- **Món ăn**: STT, Tên món, Tên danh mục, Mô tả, Giá.

Chỉ hỗ trợ `.xlsx`, tối đa 5 MB và 5.000 dòng dữ liệu mỗi sheet. File `.xls` (kể cả đổi đuôi thành `.xlsx`) bị từ chối. STT là số nguyên dương, không trùng trong sheet; được dùng để trình bày, không phải ID database. Giá dùng ô số Excel (ví dụ `55000`), không âm và tối đa hai chữ số thập phân. Mô tả có thể để trống. Không hỗ trợ ô công thức hoặc cột dữ liệu ngoài mẫu.

## Quy tắc import

- Kiểm tra toàn bộ file trước khi ghi; các thao tác lưu nằm trong cùng transaction.
- Tên danh mục được chuẩn hóa Unicode NFC, chữ hoa/thường và khoảng trắng khi tìm kiếm.
- Danh mục trong sheet chưa có trong database được tạo mới. Danh mục đã có duy nhất được dùng lại, giữ nguyên thuộc tính hiện tại.
- Danh mục mới được nối sau thứ tự hiển thị lớn nhất đang có, theo thứ tự dòng trong file. Danh mục dùng lại giữ nguyên thứ tự. STT trong Excel không thay đổi vị trí dữ liệu cũ.
- Khi xóa thành công một danh mục rỗng, toàn bộ danh mục còn lại được đánh lại thứ tự liên tục từ 1 theo thứ tự hiển thị hiện tại (nếu trùng thứ tự thì theo ID). Danh mục tạm ngưng phục vụ vẫn được tính. ID và liên kết món không thay đổi; xóa thất bại không đổi thứ tự. Lần import tiếp theo nối sau danh sách này.
- Món mới được thêm theo thứ tự dòng trong file. Danh sách món mặc định hiển thị theo thứ tự thêm (ID tăng dần), nên món nhập sau nằm sau món đã có, kể cả khi sang trang tiếp theo. Các lựa chọn sắp xếp theo tên hoặc giá vẫn có thể được chọn riêng.
- Món ăn có thể tham chiếu danh mục đã có trong database dù không khai báo lại ở sheet Danh mục.
- Nếu có nhiều danh mục trùng tên trong database, hoặc món tham chiếu tên không có trong cả database và sheet Danh mục, import bị từ chối.
- Khi có món trùng tên và cùng danh mục, backend chưa ghi dữ liệu và trả HTTP 409 với code `EXCEL_DUPLICATES_CONFIRMATION_REQUIRED`. `error.details` gồm `duplicateCount`, `itemsToCreate`, `categoriesToCreate`, `duplicates` và `confirmationToken`. Mỗi phần tử `duplicates` có `row`, `name`, `category`, `source` (`CATALOG` hoặc `FILE`) và `originalRow` (dòng đầu trong file nếu trùng trong file).
- Giao diện mở cửa sổ báo số lượng và liệt kê toàn bộ các dòng trùng. **Hủy** hoặc Esc không lưu dữ liệu. **Đồng ý, tiếp tục** gửi lại đúng file cùng multipart field `confirmationToken`. Sau xác nhận, chỉ thêm món mới; món trùng được bỏ qua và giữ nguyên giá/mô tả. Các dòng trùng trong file chỉ được thêm một lần; số dòng bỏ qua trả về trong `itemsSkipped`.
- Backend kiểm tra lại toàn bộ file khi xác nhận. Xác nhận gắn với nội dung file, danh sách trùng và số món/danh mục sẽ thêm; nếu thay đổi thì trả danh sách mới để xác nhận lại. Các lỗi dữ liệu luôn chặn import, kể cả sau khi đã xác nhận món trùng. File không có món trùng được nhập ngay sau khi kiểm tra hợp lệ.
- Món trùng tên nhưng khác danh mục bị báo lỗi, kể cả khi danh mục đã có không được nhắc tới trong file. Nếu chỉ có xung đột giữa các dòng trong file thì báo tất cả dòng xung đột. Một dòng khớp chính xác món/danh mục đã có vẫn được bỏ qua; dòng khác danh mục bị báo lỗi.
- Tên danh mục trùng trong sheet Danh mục vẫn là lỗi. Tên món và tên danh mục đều được chuẩn hóa Unicode, chữ hoa/thường và khoảng trắng khi so sánh.
- Danh mục mới mặc định hoạt động; món mới mặc định `AVAILABLE`, không nổi bật. Các thuộc tính này không có trong Excel.
- Kiểm tra và trả về toàn bộ lỗi có thể xác định trong file (các cột/dòng, cả hai sheet và xung đột database). HTTP 400 có `error.details` là mảng `{sheet, row, column, message}`; row/column bắt đầu từ 1, có thể null với lỗi cấu trúc file. Giao diện hiển thị toàn bộ lỗi trong bảng. File không đọc được được báo lỗi file. Không tạo dữ liệu một phần khi còn lỗi; cache menu được làm mới sau commit có thay đổi.

Triển khai không thay đổi schema database và không tự import dữ liệu mẫu. Cần khởi động lại backend để nạp dependency Apache POI và các endpoint mới.
