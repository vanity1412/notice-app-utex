# Deploy Vercel + Firebase

Tai lieu nay danh cho huong production: PWA dua len Vercel, backend thong bao dua sang Firebase/Google Cloud.

## Mo hinh khuyen nghi

```text
Vercel
  - Host PWA React/Vite
  - HTTPS cho Web Push va Add to Home Screen

Firebase / Google Cloud
  - Firestore: luu user, iCal URL, events, push subscriptions
  - Cloud Functions: API va job sync Moodle
  - Cloud Scheduler: goi sync moi 5-15 phut
  - FCM/Web Push: gui notification
```

Khong nen dung JSON store local cua `backend/src/store.ts` cho production serverless, vi file local khong ben vung tren Firebase Functions/Vercel Functions.

## Deploy PWA len Vercel

1. Push repo len GitHub.
2. Vao Vercel -> Add New Project -> Import GitHub repo.
3. Chon root directory:

```text
web-pwa-backend
```

4. Vercel se doc `web-pwa-backend/vercel.json`.
5. Them Environment Variable:

```text
VITE_API_BASE_URL=https://<firebase-functions-or-backend-domain>/api
```

6. Deploy.

Neu deploy bang CLI:

```powershell
cd web-pwa-backend
npm install
npm run build -w pwa
npx vercel
```

## Tao Firebase project

1. Vao Firebase Console -> Add project.
2. Bat Firestore Database.
3. Bat Cloud Messaging.
4. Tao Web Push certificate/VAPID key trong Cloud Messaging.
5. Neu dung Cloud Functions scheduler, Firebase project can lien ket billing account vi Cloud Scheduler/Functions can Google Cloud resource.

## Backend hien tai va viec can lam khi len Firebase

Backend local hien tai da co:

- Validate Moodle Calendar URL.
- Sync `.ics` qua HTTPS voi CA cua Moodle.
- Parse deadline.
- So sanh deadline moi/thay doi.
- Gui Web Push bang VAPID.
- Scheduler local bang `node-cron`.

Khi dua sang Firebase, can doi 2 lop nay:

```text
backend/src/store.ts
  JSON store -> Firestore repository

backend/src/scheduler.ts
  node-cron -> onSchedule trong Cloud Functions
```

Cac file logic nen giu:

```text
shared/src/icsParser.ts
shared/src/moodleUrl.ts
shared/src/eventLabels.ts
backend/src/sync.ts
backend/src/push.ts
```

## Bien moi truong can co

PWA tren Vercel:

```text
VITE_API_BASE_URL=https://<backend-domain>/api
```

Backend/Firebase Functions:

```text
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...
VAPID_SUBJECT=mailto:your-email@example.com
REMINDER_GRACE_MINUTES=20
```

Email neu muon dung:

```text
SMTP_HOST=...
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=...
SMTP_PASS=...
SMTP_FROM="UTE Notice <no-reply@example.com>"
```

## Luong deploy de tranh loi

1. Deploy backend/Firebase Functions truoc.
2. Test endpoint:

```text
https://<backend-domain>/api/health
```

3. Lay backend domain do gan vao `VITE_API_BASE_URL` tren Vercel.
4. Deploy PWA len Vercel.
5. Mo PWA bang HTTPS.
6. Dan iCal URL va bam Luu & dong bo.
7. Bat Web Push.
8. Bam Gui test trong tab Thong bao.

## Luu y iPhone

- iOS can 16.4 tro len.
- Nguoi dung phai Add to Home Screen.
- Web Push chi nen test tren URL HTTPS production, khong test bang IP LAN.
- Neu doi VAPID key sau khi user da subscribe, user can bat lai Web Push.

## Link tai lieu chinh thuc

- Vercel Vite deployment: https://vercel.com/docs/frameworks/frontend/vite
- Vercel environment variables: https://vercel.com/docs/environment-variables
- Firebase Cloud Messaging Web: https://firebase.google.com/docs/cloud-messaging/web/get-started
- Firebase scheduled functions: https://firebase.google.com/docs/functions/schedule-functions
- Firebase Hosting: https://firebase.google.com/docs/hosting
