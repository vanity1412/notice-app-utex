# UTE Notice

Ứng dụng Android giúp sinh viên HCMUTE theo dõi lịch kiểm tra, deadline bài nộp, quiz/test trên Moodle UTEx LMS và nhận thông báo trước khi tới hạn.

<p align="center">
  <img src="https://upload.wikimedia.org/wikipedia/commons/b/b9/Logo_Tr%C6%B0%E1%BB%9Dng_%C4%90%E1%BA%A1i_H%E1%BB%8Dc_S%C6%B0_Ph%E1%BA%A1m_K%E1%BB%B9_Thu%E1%BA%ADt_TP_H%E1%BB%93_Ch%C3%AD_Minh.png" alt="Logo HCMUTE" width="180">
</p>

## App Có Gì?

- Lưu Calendar URL/iCal URL của Moodle trong máy.
- Tự đồng bộ lịch nền khoảng 15 phút/lần khi có mạng.
- Hiển thị deadline sắp tới dạng thẻ dễ nhìn.
- Phân loại nhanh: bài nộp, kiểm tra, thi, deadline.
- Thông báo khi phát hiện deadline mới.
- Nhắc trước hạn: 7 ngày, 3 ngày, 1 ngày, 2 giờ.
- Không lưu mật khẩu Moodle.
- Không dùng `MoodleSession`/cookie trong app.

## Cách Lấy Calendar URL Từ Moodle UTEx

Truy cập trang xuất lịch:

```text
https://utexlms.hcmute.edu.vn/calendar/export.php?
```

Nếu Moodle yêu cầu đăng nhập, hãy đăng nhập bằng tài khoản trường/Google như bình thường.

![Hướng dẫn xuất lịch Moodle](docs/images/moodle-export-calendar.svg)

Sau đó làm theo các bước:

1. Ở mục **Các sự kiện được xuất**, chọn lịch bạn muốn app thông báo.
   Khuyến nghị chọn **Tất cả sự kiện** để lấy đủ deadline, quiz, lịch kiểm tra.
2. Ở mục **Khoảng thời gian**, chọn khoảng thời gian muốn theo dõi.
   Khuyến nghị chọn **Mới đây và 60 ngày tới**.
3. Bấm **Lấy địa chỉ mạng của lịch**.
4. Ở khung **Calendar URL**, bấm **Copy URL**.
5. Mở app **UTE Notice**.
6. Dán URL vừa copy vào ô kết nối lịch Moodle.
7. Bấm **Lưu & đồng bộ**.

URL đúng thường có dạng:

```text
https://utexlms.hcmute.edu.vn/calendar/export_execute.php?userid=...&authtoken=...&preset_what=...&preset_time=...
```

## Cách Dùng App

1. Cài file `app-debug.apk` trên điện thoại Android.
2. Mở app **UTE Notice**.
3. Cấp quyền thông báo nếu Android hỏi.
4. Dán Calendar URL đã copy từ Moodle.
5. Bấm **Lưu & đồng bộ**.
6. App sẽ hiển thị lịch sắp tới và tự nhắc khi có deadline mới hoặc gần tới hạn.

## Bảo Mật

- Calendar URL có `authtoken`, ai có URL này có thể xem lịch Moodle của bạn.
- Không đưa Calendar URL lên GitHub, Facebook, ảnh chụp công khai hoặc nhóm chat.
- Nếu lỡ lộ URL, hãy vào Moodle tạo lại link export mới.
- App chỉ lưu URL trong bộ nhớ local của điện thoại.
- App không lưu mật khẩu, không lưu Google token, không lưu cookie đăng nhập Moodle.

## Build APK Bằng GitHub Actions

1. Upload toàn bộ project lên GitHub.
2. Vào tab **Actions**.
3. Chọn workflow **Build Android APK**.
4. Bấm **Run workflow**.
5. Khi chạy xong, tải artifact `ute-deadline-debug-apk`.
6. Giải nén artifact để lấy `app-debug.apk`.

## Mở Code Bằng Android Studio

1. Cài Android Studio.
2. Mở thư mục:

```text
android/
```

3. Bấm **Sync Gradle**.
4. Bấm **Run** để chạy trên máy Android hoặc emulator.

## File Chính

```text
android/app/src/main/java/com/utex/deadline/MainActivity.kt       # Màn hình chính
android/app/src/main/java/com/utex/deadline/IcsParser.kt          # Parse lịch iCal
android/app/src/main/java/com/utex/deadline/DeadlineSync.kt       # Tải lịch và phát hiện deadline mới
android/app/src/main/java/com/utex/deadline/SyncWorker.kt         # Tự sync nền
android/app/src/main/java/com/utex/deadline/ReminderWorker.kt     # Nhắc trước hạn
android/app/src/main/java/com/utex/deadline/ReminderScheduler.kt  # Lên lịch sync và nhắc
```
