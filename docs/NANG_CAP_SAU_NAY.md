# Nâng cấp sau này

Bản hiện tại là đơn giản nhất: app tự sync iCal trong máy.

Nếu nhiều sinh viên dùng và bạn muốn thông báo gần realtime hơn:

```text
Android app -> gửi iCal URL + Firebase token lên backend
Backend -> sync lịch mỗi 5-15 phút
Có deadline mới -> gửi Firebase push notification về điện thoại
```

Lúc đó cần:

- Node.js backend
- Database PostgreSQL
- Firebase Cloud Messaging
- Railway hoặc Render để host backend
- Màn hình đăng ký thiết bị hoặc đăng nhập sinh viên

Không nên làm backend ngay nếu bạn chỉ cần MVP đơn giản để test vài bạn.
