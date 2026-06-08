# Build iOS bằng GitHub Actions

Workflow iOS nằm tại `.github/workflows/build-ios.yml` và chạy khi push lên `main`/`master`, khi mở pull request, hoặc khi bấm **Run workflow** thủ công.

## Artifact mặc định

Không cần cấu hình gì thêm, workflow sẽ build app iOS trên macOS runner và upload artifact:

```text
UTE-Notice-ios
```

Trong artifact có:

```text
UTE-Notice-ios-unsigned.ipa
UTE-Notice-ios-unsigned-app.zip
```

Bản unsigned dùng để kiểm tra CI/build. iPhone thật thường không cài trực tiếp bản unsigned; để cài được trên thiết bị thật, cần ký app bằng Apple Developer certificate và provisioning profile.

## Tạo IPA signed cho iPhone thật

Vào GitHub repo -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, rồi thêm:

```text
APPLE_TEAM_ID
BUILD_CERTIFICATE_BASE64
BUILD_PROVISION_PROFILE_BASE64
P12_PASSWORD
KEYCHAIN_PASSWORD
```

Ý nghĩa:

- `APPLE_TEAM_ID`: Team ID trong Apple Developer.
- `BUILD_CERTIFICATE_BASE64`: file `.p12` export từ Apple certificate, mã hóa base64.
- `BUILD_PROVISION_PROFILE_BASE64`: file `.mobileprovision` khớp bundle id `com.utex.deadline.ioslocal`, mã hóa base64.
- `P12_PASSWORD`: mật khẩu khi export file `.p12`.
- `KEYCHAIN_PASSWORD`: mật khẩu tạm cho keychain trên GitHub Actions.

Ví dụ mã hóa base64 trên macOS:

```bash
base64 -i Certificate.p12 | pbcopy
base64 -i UTEDeadlineLocal.mobileprovision | pbcopy
```

Sau khi có đủ secrets, workflow sẽ tự chạy thêm các bước archive/export và upload thêm file `.ipa` signed trong artifact `UTE-Notice-ios`.

## Chọn kiểu export

Khi chạy thủ công workflow, có thể chọn:

- `development`: cài trên thiết bị đã nằm trong development provisioning profile.
- `ad-hoc`: cài trên thiết bị đã được add UDID vào ad-hoc provisioning profile.

Nếu muốn phát hành TestFlight/App Store sau này, nên tạo workflow release riêng với App Store Connect API key.
