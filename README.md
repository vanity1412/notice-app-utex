# UTE Deadline - App Android đơn giản nhất

Mục tiêu: sinh viên dán **Moodle iCal URL** một lần, app tự kiểm tra deadline mới và thông báo trên điện thoại.

## App này có gì?

- Không cần backend
- Không cần đăng nhập
- Không cần database server
- Lưu iCal URL trong máy sinh viên
- Kiểm tra deadline nền bằng WorkManager khoảng 15 phút/lần
- Có nút **Kiểm tra ngay** để báo liền khi mở app
- Có thông báo khi phát hiện deadline mới
- Có nhắc trước hạn: 7 ngày, 3 ngày, 1 ngày, 2 giờ

## Luồng sử dụng

```text
Mở Moodle UTEx -> Calendar/Lịch -> Export calendar/iCal -> copy URL
Mở app UTE Deadline -> dán URL -> Lưu URL -> Kiểm tra ngay
```

## Cách mở code

1. Cài Android Studio.
2. Mở thư mục:

```text
android/
```

3. Bấm **Sync Gradle**.
4. Bấm **Run** để chạy trên máy Android hoặc emulator.
5. Muốn build APK: Android Studio -> Build -> Build Bundle(s) / APK(s) -> Build APK(s).

## Vì sao không dùng Python?

Python chạy trên Android được qua Termux hoặc Kivy, nhưng để sinh viên cài như app bình thường thì phức tạp hơn. Bản này dùng **Kotlin Android native**, dễ build APK và ổn định hơn cho thông báo nền.

## Lưu ý Android

Android không cho app nền chạy liên tục từng giây. Bản đơn giản dùng WorkManager nên kiểm tra nền tối thiểu khoảng 15 phút/lần. Muốn báo ngay 100% theo kiểu server push thì cần backend + Firebase Cloud Messaging.

## Bảo mật

- Không dùng MoodleSession/cookie.
- Không lưu mật khẩu Moodle.
- Mỗi sinh viên tự dán iCal URL của mình.
- iCal URL được lưu local trong máy người dùng.

## File chính

```text
android/app/src/main/java/com/utex/deadline/MainActivity.kt       # Màn hình chính
android/app/src/main/java/com/utex/deadline/IcsParser.kt          # Parse lịch iCal
android/app/src/main/java/com/utex/deadline/DeadlineSync.kt       # Tải lịch và phát hiện deadline mới
android/app/src/main/java/com/utex/deadline/SyncWorker.kt         # Tự sync nền
android/app/src/main/java/com/utex/deadline/ReminderWorker.kt     # Nhắc trước hạn
android/app/src/main/java/com/utex/deadline/ReminderScheduler.kt  # Lên lịch sync và nhắc
```
