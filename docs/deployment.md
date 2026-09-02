# 배포 (TestFlight + Google Play 내부 테스트)

클라이언트 릴리스는 **Fastlane + GitHub Actions**로 자동화한다(EAS 아님 — 근거는 [`decisions.md`](./decisions.md) ADR-008). 백엔드는 상시 배포하지 않는다(ADR-007).

- 워크플로: [`.github/workflows/release.yml`](../.github/workflows/release.yml)
- Fastlane: [`apps/client/fastlane/`](../apps/client/fastlane/) (`Fastfile` / `Appfile`)
- Android 서명 주입 플러그인: [`apps/client/plugins/withAndroidReleaseSigning.js`](../apps/client/plugins/withAndroidReleaseSigning.js)

## 개요

| | iOS | Android |
|---|---|---|
| 러너 | `macos-14` (public repo라 무료) | `ubuntu-latest` |
| 빌드 | `expo prebuild` → `fastlane gym` (manual signing) | `expo prebuild` → `gradle bundleRelease` |
| 서명 | distribution `.p12` + provisioning profile 을 임시 keychain 에 import (base64 시크릿, `match` 안 씀) | 업로드 keystore (base64 시크릿) |
| 업로드 | `upload_to_testflight` (내부 테스터) | `upload_to_play_store` track `internal`, `release_status: draft` |
| 버전 | `CURRENT_PROJECT_VERSION` = `github.run_number` | `versionCode` = `github.run_number` (플러그인이 `STORIA_VERSION_CODE` 주입) |

`app.json`의 `version`("1.0.0")이 마케팅 버전. 빌드 번호만 CI가 자동 증가.

> **iOS 서명은 Aran 프로젝트(`~/Desktop/Aran/Aran/.github/workflows/cd.yml`)와 동일한 방식.**
> Distribution 인증서(`Apple Distribution: uihyung zo (9G5T5K3BP2)`)와 ASC API 키는 **계정(팀) 단위**라 Aran 것을 그대로 재사용한다 — Aran repo 의 GitHub Secrets(`BUILD_CERTIFICATE_BASE64`, `BUILD_CERTIFICATE_PASSWORD`, `ASC_API_KEY_*`)를 Storia repo 에 복사하면 됨. 앱마다 새로 만들어야 하는 건 **provisioning profile 하나**(`com.storia.client` 용)뿐.
> 로컬 원본: `~/Desktop/인증서/certificate.p12` (Distribution 인증서), `~/Desktop/인증서/AuthKey_*.p8` (ASC API 키).

## 릴리스 커팅

```bash
git tag v1.0.0
git push origin v1.0.0        # → release.yml 의 ios + android job 실행
```

또는 Actions 탭 → **Release** → *Run workflow* 에서 `platform` (both/ios/android) 선택.

빌드 후:
- **iOS**: App Store Connect → TestFlight → 빌드 "처리 중" 끝나면 내부 테스터 그룹에 노출. 실기기에서 스모크(캐릭터 목록 → 채팅 → 음성통화). 백엔드는 이 시점에만 로컬/ngrok 기동.
- **Android**: Play Console → 테스트 → 내부 테스트 → 최신 릴리스가 **draft**로 올라옴. "검토 후 출시"로 승격하면 옵트인 링크로 설치 가능.

롤백: 이전 태그를 다시 푸시하거나(빌드 번호는 계속 증가), 스토어 콘솔에서 이전 빌드로 되돌린다.

## GitHub Secrets

Repo → Settings → Secrets and variables → Actions → *New repository secret*.

### 공통 (Firebase 설정 파일 — 둘 다 `.gitignore` 처리돼 있음)

| 시크릿 | 값 | 만드는 법 |
|---|---|---|
| `IOS_GOOGLE_SERVICES_PLIST_B64` | `GoogleService-Info.plist` 를 base64 | `base64 -i GoogleService-Info.plist \| pbcopy` |
| `ANDROID_GOOGLE_SERVICES_JSON_B64` | `google-services.json` 을 base64 | `base64 -i google-services.json \| pbcopy` |

### iOS

**대부분 Aran repo 에서 그대로 복사.** Aran → Settings → Secrets 에서 값을 보고 Storia 에 같은 이름으로 넣으면 됨 (GitHub 은 값을 다시 보여주진 않으니, 로컬 원본 파일에서 재생성).

| 시크릿 | 값 | 만드는 법 |
|---|---|---|
| `ASC_API_KEY_ID` | App Store Connect API 키 ID | Aran 것 재사용. 없으면 ASC → Users and Access → Integrations → **App Store Connect API** → 팀 키 생성(역할: App Manager). Key ID 는 `~/Desktop/인증서/AuthKey_<ID>.p8` 파일명 |
| `ASC_API_KEY_ISSUER_ID` | 같은 화면 상단의 Issuer ID | 〃 |
| `ASC_API_KEY_CONTENT` | `.p8` 파일 내용을 base64 | `base64 -i ~/Desktop/인증서/AuthKey_XXXXXX.p8 \| pbcopy` (ASC API 키인 쪽 — APNs 키와 헷갈리지 말 것, ASC 콘솔에서 Key ID 대조) |
| `BUILD_CERTIFICATE_BASE64` | distribution `.p12` 를 base64 | `base64 -i ~/Desktop/인증서/certificate.p12 \| pbcopy` (또는 Keychain Access 에서 "Apple Distribution: uihyung zo" 우클릭 → 내보내기) |
| `BUILD_CERTIFICATE_PASSWORD` | 그 `.p12` 의 비밀번호 | Aran 설정 때 지정한 값. 모르면 Keychain 에서 새 비밀번호로 다시 export |
| `BUILD_PROVISION_PROFILE_BASE64` | **`com.storia.client` 용** App Store provisioning profile 을 base64 | 아래 "1회성 셋업" 2번 — 이게 유일하게 새로 만드는 것 |
| `KEYCHAIN_PASSWORD` | CI 임시 keychain 비밀번호 (아무 값) | 임의 문자열 |

`APPLE_TEAM_ID`(`9G5T5K3BP2`)·`APPLE_ID`(`lukaend@naver.com`)는 `Appfile` 에 기본값으로 박혀 있어 시크릿 불필요.

### Android

| 시크릿 | 값 | 만드는 법 |
|---|---|---|
| `ANDROID_KEYSTORE_B64` | 업로드 keystore를 base64 | 아래 "1회성 셋업" 4번 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 비밀번호 | 〃 |
| `ANDROID_KEY_ALIAS` | 키 alias (예: `storia-upload`) | 〃 |
| `ANDROID_KEY_PASSWORD` | 키 비밀번호 (보통 keystore 비밀번호와 동일) | 〃 |
| `PLAY_SERVICE_ACCOUNT_JSON_B64` | Play Console 서비스계정 JSON을 base64 (`base64 -i play-sa.json | pbcopy`) | 아래 "1회성 셋업" 5번 |

## 1회성 셋업 (자동화 불가 — 사람이 한 번)

1. **App Store Connect 에 앱 레코드 생성** — bundle ID `com.storia.client`, 이름 "Storia", SKU 아무거나. (앱이 없으면 `pilot` 업로드가 404.)
2. **`com.storia.client` provisioning profile 생성** (iOS 에서 유일하게 새로 만드는 것):
   - developer.apple.com → Certificates, IDs & Profiles → **Identifiers** → `com.storia.client` App ID 등록 (Push Notifications capability 켜기 — FCM 용).
   - **Profiles** → `+` → **App Store** 배포 → App ID `com.storia.client` 선택 → 인증서는 기존 `Apple Distribution: uihyung zo` 선택 → 이름 예: `Storia App Store` → 생성 → `.mobileprovision` 다운로드.
   - `base64 -i Storia_App_Store.mobileprovision | pbcopy` → `BUILD_PROVISION_PROFILE_BASE64`.
   - (대안: `cd apps/client && bundle exec fastlane run get_provisioning_profile app_identifier:com.storia.client api_key_path:... ` 로 CLI 자동 생성.)
3. **iOS 나머지 시크릿은 Aran 것 재사용** — `BUILD_CERTIFICATE_BASE64` / `BUILD_CERTIFICATE_PASSWORD` / `ASC_API_KEY_*` 를 위 "GitHub Secrets → iOS" 표대로 로컬 원본(`~/Desktop/인증서/`)에서 재생성해 Storia repo 에 넣는다. 인증서·API 키는 팀 단위라 앱이 달라도 그대로 동작.
4. **Android 업로드 keystore**:
   ```bash
   keytool -genkeypair -v -keystore storia-upload.keystore \
     -alias storia-upload -keyalg RSA -keysize 2048 -validity 10000
   base64 -i storia-upload.keystore | pbcopy   # → ANDROID_KEYSTORE_B64
   ```
   **원본 keystore 를 안전하게 보관**(예: 비밀번호 관리자). Play App Signing 에 등록해두면 분실해도 업로드 키 재설정이 가능하지만, 등록 전 분실 시 앱 업데이트 불가.
5. **Play Console**:
   - 앱 생성(패키지 `com.storia.client`), 내부 테스트 트랙 활성화, 내부 테스터 목록에 본인 이메일.
   - Setup → **API access** → 서비스계정 생성 → JSON 키 다운로드 → 그 서비스계정에 "출시 관리자" 또는 최소 "출시" 권한 부여 → `base64 -i play-sa.json | pbcopy` → `PLAY_SERVICE_ACCOUNT_JSON_B64`.
   - **최초 1개 AAB 는 콘솔에서 수동 업로드**해야 트랙이 활성화된다(Play 정책). 이후부터 `fastlane` 자동 업로드.
6. **`app.json` — TestFlight 전환 시**: `ios.entitlements.aps-environment` 를 `"production"` 으로. APNs `.p8` 인증 키는 dev/prod 공용이라 Firebase 재업로드 불필요.

## 로컬에서 돌려보기

```bash
cd apps/client
bundle install
bundle exec fastlane lanes          # ios beta / android beta 확인

# Android (keystore 생성 후, 업로드 직전까지)
npx expo prebuild --platform android
STORIA_UPLOAD_STORE_FILE=$PWD/storia-upload.keystore \
STORIA_UPLOAD_STORE_PASSWORD=... STORIA_UPLOAD_KEY_ALIAS=storia-upload STORIA_UPLOAD_KEY_PASSWORD=... \
BUILD_NUMBER=$(date +%s) \
  bundle exec fastlane run gradle task:bundle build_type:Release project_dir:android/ \
    properties:'{"STORIA_UPLOAD_STORE_FILE":"'"$PWD"'/storia-upload.keystore", ... }'
jarsigner -verify -verbose android/app/build/outputs/bundle/release/app-release.aab
```

> 로컬 시스템 Ruby 가 2.6 이면 `fastlane ~> 2.223` 이 안 돌아간다. `rbenv`/`asdf` 로 Ruby 3.3 을 깔거나, CI 에서만 실행한다.

## CI 검증 순서 (권장)

1. `workflow_dispatch` → `platform: android` 만 실행 → AAB 아티팩트 + Play internal **draft** 도착 확인.
2. `platform: ios` 실행 → TestFlight "처리 중" 빌드 등장 확인.
3. 문제 없으면 `git tag v1.0.0 && git push origin v1.0.0` 로 both.
4. 기존 `ci.yml`(test/tsc/jest)은 그대로 — `release.yml` 과 독립.
