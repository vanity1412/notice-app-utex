# Backend

Thu muc nay danh cho backend sync Moodle va gui notification.

## Du kien chua

```text
src/
  api/                 # HTTP endpoints
  jobs/                # Scheduled jobs
  services/            # Moodle sync, push, auth, storage
  repositories/        # Doc/ghi database
  config/              # Env va runtime config
```

## Nhiem vu chinh

- Nhan va validate iCal URL tu PWA.
- Luu thong tin user/device/push subscription.
- Dinh ky tai file `.ics` tu Moodle.
- Parse deadline va so sanh thay doi.
- Gui Web Push khi deadline moi, thay doi, hoac sap toi han.
