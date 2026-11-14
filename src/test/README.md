# Unit test backend ROMS — toàn bộ dự án

Trong thư mục `roms-backend`, dùng Java 21 và Maven:

```powershell
mvn -Punit-tests test
# Hoặc: .\run-unit-tests.ps1
```

Code ở `src/test/java/com/roms/`. Các lớp bổ sung cho báo cáo:

- `service/AuthServiceUnitTest.java`: 28 ca đăng nhập, refresh, logout, hồ sơ, OTP và reset mật khẩu.
- `service/RefreshTokenServiceUnitTest.java`: 7 ca tạo/xoay/thu hồi phiên bằng repository mock.
- `common/CsvExportUtilTest.java`: 6 ca UTF-8 BOM, dữ liệu trống, làm tròn và thứ tự dòng.
- `security/TokenHashUtilTest.java`: 5 ca SHA-256, token null, đối chiếu hash và mã hóa token ngẫu nhiên.

Các lớp bổ sung tiếp cho phạm vi toàn dự án:

- `service/UserServiceUnitTest.java`: 21 ca CRUD nhân viên/tablet, chuẩn hóa email, gán bàn, khóa tài khoản, hồ sơ và đổi mật khẩu.
- `service/EmailServiceUnitTest.java`: 3 ca nội dung OTP, thiếu cấu hình và lỗi SMTP, không gửi email thật.
- `service/PrinterServiceUnitTest.java`: 7 ca danh sách thiết bị, cấu hình, phiếu bếp và hóa đơn; mock PrintServiceLookup và executor, không in thật.
- `security/SecurityUtilsTest.java`: 3 ca security context, principal không đáng tin và principal tablet.
- `service/MenuContextInvalidatorTest.java`: 3 ca transaction bắt buộc, chỉ invalidate sau commit, không invalidate khi rollback.
- `service/impl/CatalogAccessTest.java`: 3 ca anonymous/owner/non-owner.

Tổng **86 ca bổ sung**, dùng JUnit/Mockito/AssertJ có sẵn. Không thay đổi logic ứng dụng. Các test sẵn có cho Order, Kitchen, Bill, Menu/Category/Image/Excel, Inventory, Purchase, Recipe, Expense, Promotion, Dashboard, Tax/Settings, AI và realtime được giữ và chạy lại. Báo cáo có sheet đối chiếu cả **34 service/helper** với source test thật, gồm lớp tham chiếu trực tiếp implementation.

Kết quả toàn bộ profile ngày 14/11/2026: **487 Passed, 0 Failed, 0 Skipped**. Bao gồm unit test và component test HTTP/security dùng mock. XML thực thi nằm trong `target/roms-unit-reports`. Đây là số testcase, không phải tỷ lệ bao phủ dòng/nhánh.

Profile loại các lớp `@SpringBootTest` cần hạ tầng, PostgreSQL/Cloudinary live và test có thể gửi lệnh in thật. **82 ca integration/môi trường chưa được chạy** trong báo cáo. Lệnh `mvn test` mặc định vẫn có thể chạy các lớp này; cần môi trường test riêng nếu muốn xác minh tiếp.
