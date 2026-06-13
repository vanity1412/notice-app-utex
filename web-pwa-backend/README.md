# UTE Notice Web PWA + Backend

Huong moi cho UTE Notice khi khong phat hanh app iOS native: sinh vien dung web/PWA, backend sync Moodle iCal va gui Web Push.

## Cau truc

```text
web-pwa-backend/
  pwa/                 # Frontend PWA cho sinh vien
  backend/             # API, scheduler, worker sync Moodle va push
  shared/              # Kieu du lieu, parser, validation dung chung
  docs/                # Tai lieu thiet ke va trien khai huong web
```

## Stack de xuat

```text
Frontend: React + Vite PWA
Backend: Node.js + Express
Database local: JSON store atomic write
Push: Web Push
Scheduler: node-cron
```

## Luong chinh

```text
Sinh vien dang nhap
-> dan Moodle iCal URL
-> backend luu URL/token an toan
-> scheduler dinh ky tai file .ics
-> parse deadline
-> luu deadline vao database
-> gui Web Push khi co deadline moi/sap toi han
```

## Chay local

```powershell
npm install
npm run dev:backend
npm run dev:pwa
```

Mo PWA tai:

```text
http://localhost:5173
```

Backend API mac dinh:

```text
http://localhost:8787/api
```

## Cau hinh Web Push

Tao VAPID key:

```powershell
npm run generate:vapid
```

Copy ket qua vao `backend/.env`:

```text
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:your-email@example.com
```

Neu khong cau hinh VAPID key, backend se tao key tam thoi moi lan chay; chi phu hop dev local.

## Len production

- PWA phai chay tren HTTPS de Web Push hoat dong on dinh.
- iPhone can iOS 16.4+ va nguoi dung phai Add to Home Screen.
- Backend can chay 24/7 de cron sync Moodle va gui notification.
- Neu nhieu sinh vien dung that, nen doi `backend/src/store.ts` sang Firestore/PostgreSQL.
- Huong dan Vercel + Firebase: xem `docs/DEPLOY_VERCEL_FIREBASE.md`.
