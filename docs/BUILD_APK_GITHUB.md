# Build APK bằng GitHub Actions

Nếu máy chưa cài Android Studio/Android SDK, dùng GitHub Actions để build APK:

1. Tạo repo GitHub mới.
2. Upload toàn bộ project này lên repo.
3. Vào tab **Actions**.
4. Chọn workflow **Build Android APK**.
5. Bấm **Run workflow**.
6. Khi chạy xong, kéo xuống phần **Artifacts** và tải file `UTE-Notice-apk`.
7. Giải nén artifact sẽ có `UTE-Notice.apk` để cài thử trên điện thoại Android.

Lưu ý: file debug APK dùng để test nội bộ. Khi phát hành rộng, cần tạo release APK/AAB và ký bằng keystore riêng.
