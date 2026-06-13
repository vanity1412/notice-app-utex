# Kien Truc PWA + Backend

## Muc tieu

- Khong can phat hanh app iOS qua App Store.
- Sinh vien dung bang link web va Add to Home Screen.
- Backend thay dien thoai sync Moodle de iOS van nhan thong bao khi web khong mo.

## Module

```text
PWA
  - UI lich deadline
  - Dang nhap
  - Luu iCal URL
  - Dang ky Web Push

Backend
  - API user/settings
  - Scheduler sync Moodle
  - ICS parser
  - Notification dispatcher

Database
  - users
  - devices
  - calendars
  - deadlines
  - notification_logs
```

## Goi y database

```text
users/{userId}
devices/{deviceId}
calendars/{calendarId}
deadlines/{deadlineId}
notification_logs/{logId}
```

## Luu y bao mat

Moodle iCal URL co `authtoken`, nen can luu o backend voi quyen doc/ghi chat che. Khong hien full token tren UI, khong log URL day du, va nen cho sinh vien xoa ket noi bat cu luc nao.
