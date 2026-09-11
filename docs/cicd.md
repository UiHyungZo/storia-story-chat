# CI/CD — Storia

이 문서는 Storia의 GitHub Actions 파이프라인 두 개(`ci.yml`, `release.yml`)가 **무엇을 / 왜 / 어떻게** 하는지, 그리고 처음부터 세팅하는 전 과정을 정리한다.

- 실행에 필요한 시크릿 표와 1회성 계정 셋업은 → [`deployment.md`](./deployment.md)
- 빌드 툴을 EAS가 아니라 Fastlane으로 정한 근거 → [`decisions.md`](./decisions.md) **ADR-008**
- 테스트 스위트 상세 → [`testing.md`](./testing.md)

---

## 1. 전체 그림

| 워크플로 | 파일 | 트리거 | 하는 일 | 러너 |
|---|---|---|---|---|
| **CI** | `.github/workflows/ci.yml` | 모든 `push` / `pull_request` (main, develop) | 백엔드 + 클라이언트 테스트 | `ubuntu-latest` |
| **Release (CD)** | `.github/workflows/release.yml` | 태그 `v*` push, 또는 수동 실행 | iOS→TestFlight / Android→Play 내부 테스트 | iOS: `macos-26`, Android: `ubuntu-latest` |

**두 워크플로는 독립적이다.** CI는 코드가 바뀔 때마다 돌고, Release는 "이제 배포한다"는 의도적 액션(태그 또는 버튼)에서만 돈다. 서로를 게이트하지 않는다 — 태그는 이미 CI를 통과해 머지된 커밋에만 붙기 때문이다.

```
개발 → push → [CI] 테스트 통과 → 머지
                                      │
              배포 결정 → git tag v1.0.0 → [Release] ─┬─ iOS  job → .ipa → TestFlight
                       또는 Actions 버튼            └─ Android job → .aab → Play (internal, draft)
```

---

## 2. CI (`ci.yml`)

### 목적
"만들어봤다"가 아니라 "회귀를 막을 수 있다"를 증명. 외부 계정/시크릿 없이 순수하게 도는 것이 설계 원칙 — 로컬 MariaDB 대신 H2, 시뮬레이터 없이 Jest.

### `backend` job
```yaml
runs-on: ubuntu-latest
steps:
  - checkout
  - setup-java (temurin 17)
  - gradle/actions/setup-gradle
  - ./gradlew test --no-daemon
```
- **DB 불필요**: `src/test/resources/application.yml`이 테스트 클래스패스에서 datasource를 H2 인메모리로 오버라이드(테스트 클래스패스가 메인보다 우선). 로컬에 MariaDB가 없어도, CI 러너에도 없어도 전체 스위트가 돈다.
- 18개 테스트: 서비스 단위(Mockito), `@DataJpaTest`(리포지토리), `@WebMvcTest`(컨트롤러 슬라이스), 전역 예외 처리기, 컨텍스트 로드 스모크.

### `client` job
```yaml
runs-on: ubuntu-latest
steps:
  - checkout
  - setup-node (20, npm 캐시)
  - npm ci
  - npx tsc --noEmit      # 타입 체크
  - npx jest --ci         # 단위 17 + RNTL UI 17 = 34개
```
- `jest --ci`는 경로 필터가 없어서, `src/**/__tests__/*.test.tsx`를 새로 추가하면 워크플로 수정 없이 자동 포함된다.

---

## 3. Release / CD (`release.yml`)

### 3.1 왜 이 구조인가 (요약, 상세는 ADR-008)

- **EAS 아님**: 타겟 채용 공고들이 요구하는 건 "네이티브 앱 CI/CD 환경을 **직접 구축**한 경험"이지 특정 서비스가 아니다. 공고의 "Expo"는 프레임워크를 뜻하고, 그건 이미 config plugin/prebuild/dev-client로 충족.
- **저장소가 public** → GitHub Actions **macOS 러너가 무제한 무료**. Fastlane의 유일한 단점(러너 비용)이 사라짐.
- **`fastlane match` 아님, `.p12` 직접**: 기존 Aran 프로젝트(`~/Desktop/Aran/Aran/.github/workflows/cd.yml`)에 이미 `.p12`-임시-keychain 방식이 돌고 있었고, distribution 인증서·ASC API 키는 **팀(계정) 단위**라 그대로 재사용 가능. private 인증서 저장소·deploy key가 통째로 불필요.

### 3.2 트리거

```yaml
on:
  push:
    tags: ["v*"]          # git tag v1.0.0 && git push origin v1.0.0  → both 빌드
  workflow_dispatch:       # Actions 탭에서 수동, platform 선택 (both/ios/android)
    inputs:
      platform: { type: choice, options: [both, ios, android], default: both }
```

각 job의 `if`는 `github.event_name == 'push' || contains(fromJSON('["both","ios"]'), inputs.platform)` 꼴 — 태그 push면 둘 다, 수동이면 고른 것만.

> **중요**: `workflow_dispatch`의 "Run workflow" 버튼은 워크플로 파일이 **기본 브랜치(`main`)에 있어야** 나타난다. feature 브랜치에만 있으면 안 보인다. → 이 저장소는 개발을 `develop`에서 하므로, 파이프라인 변경 시 `develop` 커밋 후 `main`도 fast-forward push 해야 한다.

### 3.3 버전 번호

- `BUILD_NUMBER: ${{ github.run_number }}` — 워크플로 실행 카운터. 스토어를 조회하지 않고도 항상 단조 증가.
- iOS: `increment_build_number`가 `CURRENT_PROJECT_VERSION`(= `CFBundleVersion`)에 세팅. 프로젝트는 `VERSIONING_SYSTEM = "apple-generic"`이라 `agvtool`이 동작.
- Android: `withAndroidReleaseSigning.js`가 `versionCode`를 `STORIA_VERSION_CODE` 프로퍼티로 오버라이드 가능하게 만들어두고, Fastfile이 `github.run_number`를 넘김.
- 마케팅 버전("1.0.0")은 `app.json`의 `version`. 이건 CI가 안 건드림.

---

## 4. iOS job 단계별 (`macos-26`)

| # | 스텝 | 하는 일 | 왜 |
|---|---|---|---|
| 1 | `actions/checkout` | 소스 체크아웃 | |
| 2 | **Select Xcode 26.4** | `/Applications/Xcode*.app`을 순회하며 `Info.plist`의 `CFBundleShortVersionString`이 `26.4`로 시작하는 걸 찾아 `xcode-select -s` (없으면 명시적으로 fail) | RN 0.86은 Xcode ≥16.1 필요하고, `expo-modules-jsi`의 로컬 SPM 패키지가 `swift-tools-version: 6.2`를 선언해서 **26.x 계열만 애초에 옵션**(16.x는 패키지 해석 자체가 실패, 아래 8절 #6). 근데 26.x 안에서도 세부 버전마다 다른 컴파일러 버그가 있었음 — 26.0.1/26.1.1은 `weak let`을 거부(Swift 6.2엔 없는 문법), 26.2/26.3은 `RuntimeScheduler.h`의 `SWIFT_RETURNS_RETAINED`/`SWIFT_SHARED_REFERENCE` 어노테이션 페어링을 "not returning a SWIFT_SHARED_REFERENCE type"으로 거부(클래스가 정확히 그 매크로로 어노테이션돼있는데도 — 그 시점 컴파일러 자체의 버그로 판단), **26.4에서 고쳐짐**(로컬에서 26.4.0 클린 빌드로 확인). `macos-15` 이미지는 26.3까지만 갖고 있어서 "latest 아무거나"를 골라도 항상 이 버그를 만남 → **러너를 `macos-26` 이미지(26.4.1/26.5/26.6 보유)로 바꾸고**, 그 이미지의 "latest"(26.6, 미검증)를 그냥 믿는 대신 로컬 클린 빌드로 검증된 **26.4 계열을 명시적으로 골라** 리스크를 낮춤(아래 8절 #7~#8) |
| 3 | `actions/setup-node` (20) | Node + npm 캐시 | |
| 4 | `npm ci` | 클라이언트 의존성 | prebuild/pod install이 `node_modules`를 읽음 |
| 5 | **Restore GoogleService-Info.plist** | base64 시크릿 → `apps/client/GoogleService-Info.plist` | 이 파일은 `.gitignore` 처리됨. `app.json`이 참조하므로 prebuild 전에 있어야 함 |
| 6 | **expo prebuild --platform ios --no-install** | `ios/` 네이티브 프로젝트 생성 (Podfile, `.xcodeproj` 등) | `ios/`는 커밋 안 하고 매번 재생성하는 구조 |
| 7 | **npx pod-install** | `cd ios && pod install` → `Storia.xcworkspace` + `Pods/` 생성 | prebuild가 CI에서 pod install을 안 하거나 조용히 실패 → workspace가 없어 `build_app`이 터졌음. 분리해서 실패를 노출 |
| 8 | **Verify workspace** | `test -d ios/Storia.xcworkspace \|\| exit 1` | pod install이 실패했는데 다음 스텝까지 흘러가는 것 방지 |
| 9 | `ruby/setup-ruby` (3.3) | Fastlane 실행용 Ruby | 러너 시스템 Ruby는 낡음 |
| 10 | **Install gems** | `gem install bundler -v 2.5.23 && bundle install --jobs 3` | 러너가 자동 설치하는 Bundler 4.0.x가 frozen 모드에서 lockfile CHECKSUMS로 터짐 → 2.5.23 고정 (`BUNDLER_VERSION` env) |
| 11 | **Install signing certificate and provisioning profile** | 아래 4.1 | `.p12` + 프로파일을 임시 keychain에 넣고 프로파일 **이름**을 `PROVISIONING_PROFILE_SPECIFIER` env로 export |
| 12 | **fastlane ios beta** | 아래 4.2 | 실제 빌드 + 서명 + TestFlight 업로드 |
| 13 | **Clean up keychain** (`if: always()`) | `security delete-keychain` | 임시 keychain 정리 |
| 14 | `upload-artifact` (`if: always()`) | `build/ios/*.ipa` 저장 (14일) | 업로드 실패해도 IPA는 받아볼 수 있게 |

### 4.1 인증서/프로파일 keychain import (스텝 11)

Apple의 공식 "Installing an Apple certificate on macOS runners" 레시피. Aran과 동일.

```bash
CERT_PATH="$RUNNER_TEMP/dist_certificate.p12"
PP_PATH="$RUNNER_TEMP/storia_pp.mobileprovision"
KEYCHAIN_PATH="$RUNNER_TEMP/app-signing.keychain-db"

# 1) 시크릿(base64) → 파일
echo "$BUILD_CERTIFICATE_BASE64"      | base64 --decode > "$CERT_PATH"
echo "$BUILD_PROVISION_PROFILE_BASE64" | base64 --decode > "$PP_PATH"

# 2) 임시 keychain 만들고 잠금 해제
security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"   # 6h 뒤 자동 잠금
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

# 3) .p12(인증서+개인키) import, codesign이 접근하도록 partition-list 설정
security import "$CERT_PATH" -P "$BUILD_CERTIFICATE_PASSWORD" -A -t cert -f pkcs12 -k "$KEYCHAIN_PATH"
security set-key-partition-list -S apple-tool:,apple: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security list-keychain -d user -s "$KEYCHAIN_PATH"           # 검색 목록에 추가

# 4) 프로파일 설치 + 이름 추출 → 다음 스텝에서 쓰게 env로
mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
UUID=$(/usr/libexec/PlistBuddy -c "Print UUID" /dev/stdin <<< "$(security cms -D -i "$PP_PATH")")
NAME=$(/usr/libexec/PlistBuddy -c "Print Name" /dev/stdin <<< "$(security cms -D -i "$PP_PATH")")
cp "$PP_PATH" "$HOME/Library/MobileDevice/Provisioning Profiles/$UUID.mobileprovision"
echo "PROVISIONING_PROFILE_SPECIFIER=$NAME" >> "$GITHUB_ENV"
```

`security cms -D`는 `.mobileprovision`(CMS 서명된 plist)을 평문 plist로 풀고, `PlistBuddy`로 UUID/Name을 뽑는다. Fastfile은 이 **Name**으로 프로파일을 지정한다.

### 4.2 `fastlane ios beta` (스텝 12)

`apps/client/fastlane/Fastfile`:

```ruby
lane :beta do
  api_key = app_store_connect_api_key(
    key_id: ENV.fetch("ASC_API_KEY_ID"),
    issuer_id: ENV.fetch("ASC_API_KEY_ISSUER_ID"),
    key_content: ENV.fetch("ASC_API_KEY_CONTENT"),   # base64 .p8
    is_key_content_base64: true, in_house: false
  )

  profile_name = ENV.fetch("PROVISIONING_PROFILE_SPECIFIER")   # 스텝 11이 export

  update_code_signing_settings(       # 자동 서명 끄고 수동으로 고정
    use_automatic_signing: false, path: "ios/Storia.xcodeproj",
    team_id: "9G5T5K3BP2", targets: ["Storia"],
    code_sign_identity: "Apple Distribution",
    profile_name: profile_name, bundle_identifier: "com.storia.client"
  )

  increment_build_number(xcodeproj: "ios/Storia.xcodeproj", build_number: BUILD_NUMBER)

  build_app(                          # = gym = xcodebuild archive + export
    workspace: "ios/Storia.xcworkspace", scheme: "Storia", configuration: "Release",
    export_method: "app-store", output_directory: "build/ios", output_name: "Storia.ipa",
    export_options: { signingStyle: "manual",
                      provisioningProfiles: { "com.storia.client" => profile_name } },
    clean: true
  )

  upload_to_testflight(api_key: api_key, skip_waiting_for_build_processing: true,
                       distribute_external: false, notify_external_testers: false)
end
```

- **`app_store_connect_api_key`** — `.p8` 기반 JWT로 App Store Connect API 인증. Apple ID/비번/2FA 불필요.
- **`update_code_signing_settings`** — Expo가 생성한 프로젝트는 자동 서명이 기본. CI에선 수동 서명이 예측 가능 → team/identity/profile/bundleId를 pbxproj에 박음.
- **`build_app`** — `xcodebuild archive` 후 `-exportArchive`. Pods가 있으니 `.xcworkspace`(프로젝트 아님)를 써야 함.
- **`upload_to_testflight`** — `skip_waiting_for_build_processing: true`라 업로드만 하고 끝(Apple 처리는 몇 분 뒤 완료). `distribute_external: false` = 내부 테스터만.

### 4.3 job-level env: Sentry

```yaml
env:
  SENTRY_DISABLE_AUTO_UPLOAD: "true"
  EXPO_PUBLIC_SENTRY_DSN: ${{ secrets.EXPO_PUBLIC_SENTRY_DSN }}
```

- `@sentry/react-native`가 심어둔 Xcode 빌드 스크립트(소스맵 업로드 + "Upload Debug Symbols to Sentry" 스텝)는 `SENTRY_AUTH_TOKEN`/org 설정 없이 돌면 `error: An organization ID or slug is required (provide with --org)`로 **아카이브 전체를 실패**시킴. sourcemap 업로드용 CI 연동(auth token)은 아직 안 만들어서 `SENTRY_DISABLE_AUTO_UPLOAD=true`로 끔 — 로컬 `expo run:ios`가 이미 같은 이유로 이 값을 씀(`docs/deployment.md`).
- `EXPO_PUBLIC_SENTRY_DSN`은 Metro 번들 시점에 `process.env`에서 인라인되므로, `expo prebuild`가 아니라 **빌드(JS 번들링이 일어나는) 스텝 전체가 실행되는 job 레벨**에 있어야 함. `App.tsx`는 DSN이 비어있으면 `Sentry.init({ enabled: false })`로 무해하게 꺼짐 — 값이 있으면 그때부터 크래시 리포팅이 켜짐. 소스맵 업로드가 꺼져있어 스택트레이스는 당분간 minified 상태.

---

## 5. Android job 단계별 (`ubuntu-latest`)

| # | 스텝 | 하는 일 |
|---|---|---|
| 1 | checkout | |
| 2 | setup-node (20) | |
| 3 | `npm ci` | |
| 4 | setup-java (temurin 17) | Gradle 빌드용 |
| 5 | setup-ruby (3.3) + Install gems (Bundler 2.5.23) | Fastlane용 |
| 6 | **Restore google-services.json** | base64 시크릿 → `apps/client/google-services.json` (gitignore됨) |
| 7 | **Restore upload keystore** | base64 시크릿 → `apps/client/storia-upload.keystore` |
| 8 | **Restore Play service account** | base64 시크릿 → `apps/client/fastlane/play-service-account.json` |
| 9 | **expo prebuild --platform android** | `android/` 생성 |
| 10 | **fastlane android beta** | 아래 |
| 11 | `upload-artifact` | `app-release.aab` (14일) |

### `fastlane android beta`

```ruby
lane :beta do
  keystore = ENV.fetch("STORIA_UPLOAD_STORE_FILE")   # 스텝 7이 만든 경로
  gradle(
    task: "bundle", build_type: "Release", project_dir: "android/",
    properties: {
      "STORIA_UPLOAD_STORE_FILE"     => File.expand_path(keystore),
      "STORIA_UPLOAD_STORE_PASSWORD" => ENV.fetch("STORIA_UPLOAD_STORE_PASSWORD"),
      "STORIA_UPLOAD_KEY_ALIAS"      => ENV.fetch("STORIA_UPLOAD_KEY_ALIAS"),
      "STORIA_UPLOAD_KEY_PASSWORD"   => ENV.fetch("STORIA_UPLOAD_KEY_PASSWORD"),
      "STORIA_VERSION_CODE"          => BUILD_NUMBER,
    }
  )
  upload_to_play_store(
    track: "internal", release_status: "draft",
    aab: "android/app/build/outputs/bundle/release/app-release.aab",
    json_key: ENV.fetch("PLAY_JSON_KEY_FILE"),
    skip_upload_apk: true, skip_upload_metadata: true,
    skip_upload_images: true, skip_upload_screenshots: true
  )
end
```

- `gradle task:bundle build_type:Release` → `:app:bundleRelease` → `app-release.aab`.
- `-P` 프로퍼티로 서명 정보를 넘기면 아래 config plugin이 만든 `release` signingConfig가 읽는다.
- `release_status: "draft"` — 자동으로 테스터에게 배포하지 않고 초안으로. 스모크 후 Play Console에서 "검토 후 출시"로 승격.

### `withAndroidReleaseSigning.js` (config plugin)

`android/`는 `expo prebuild`로 매번 재생성되므로, 커밋된 `build.gradle`에 서명 설정을 넣어둘 수 없다. 이 플러그인이 prebuild 시점에 `android/app/build.gradle`을 패치한다:

1. `signingConfigs { }`에 `release` 블록 추가 — `STORIA_UPLOAD_*` **Gradle 프로퍼티**에서 keystore 경로/비번을 읽음
2. `buildTypes.release.signingConfig`를 `project.hasProperty('STORIA_UPLOAD_STORE_FILE') ? signingConfigs.release : signingConfigs.debug`로 변경 — **프로퍼티가 없으면 debug 서명으로 폴백**하므로 로컬 `assembleDebug`나 CI 테스트 잡이 안 깨짐
3. `versionCode`를 `STORIA_VERSION_CODE` 프로퍼티로 오버라이드 가능하게

멱등(이미 패치됐으면 skip)하고, 패치 실패 시 명시적으로 throw한다.

### job-level env: Sentry (iOS와 동일한 이유)

```yaml
env:
  SENTRY_DISABLE_AUTO_UPLOAD: "true"
  EXPO_PUBLIC_SENTRY_DSN: ${{ secrets.EXPO_PUBLIC_SENTRY_DSN }}
```

RN Sentry Gradle 플러그인이 붙이는 `:app:createBundleReleaseJsAndAssets_SentryUpload...` 태스크가 iOS와 똑같이 "An organization ID or slug is required"로 `gradle bundle` 전체를 실패시킴 — 같은 env var로 끔.

---

## 6. 처음부터 세팅하기 (2026-09-02~03에 실제로 한 순서)

### 6.1 준비물 매핑

| 필요한 것 | 어디서 | Storia에선 |
|---|---|---|
| iOS distribution 인증서(`.p12`) | Apple 개발자 포털 / Keychain | **Aran 것 재사용** (팀 `9G5T5K3BP2`, 만료 2027-06). `~/Desktop/인증서/certificate.p12` |
| ASC API 키(`.p8` + Key ID + Issuer ID) | ASC → Users and Access → Integrations | **Aran 것 재사용** (계정 단위). Key ID `BF5X53U7JH` |
| App ID `com.storia.client` | 개발자 포털 → Identifiers | 이번에 등록 (Push Notifications capability) |
| App Store provisioning profile | 개발자 포털 → Profiles | 이번에 생성 ("Storia App Store") |
| ASC 앱 레코드 | App Store Connect → 나의 앱 → + | 생성 필요 (`pilot` 업로드 대상) |
| Android 업로드 keystore | `keytool` | 이번에 생성. `~/Desktop/인증서/storia-upload.keystore`, alias `storia-upload` |
| Play Console 앱 + 내부 테스트 트랙 | Play Console | **계정 본인 인증(며칠) 대기 중** |
| Play 서비스계정 JSON | Play Console → API 액세스 → GCP | 인증 후 |

### 6.2 iOS provisioning profile 만들기

1. 개발자 포털 → **Identifiers** → `+` → App IDs → App → Bundle ID `com.storia.client`, **Push Notifications** 체크 → Register
2. **Profiles** → `+` → Distribution → **App Store** → App ID 선택 → 인증서 `Apple Distribution: uihyung zo` 선택 → 이름 `Storia App Store` → Generate → `.mobileprovision` 다운로드

### 6.3 Android keystore 만들기

```bash
keytool -genkeypair -v \
  -keystore ~/Desktop/인증서/storia-upload.keystore \
  -alias storia-upload -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<PW>' -keypass '<PW>' \
  -dname "CN=Storia, OU=Dev, O=Storia, L=Seoul, C=KR"
```
> keystore 원본은 **반드시 백업**. 분실 시 Play App Signing 등록 전이면 앱 업데이트 불가.

### 6.4 GitHub Secrets 15개

`base64 -i <파일> | pbcopy` 로 파일을 base64로 만들어 붙여넣는다. `pbcopy < 파일.txt` 방식이 복사 실수가 없다. 전체 표는 [`deployment.md`](./deployment.md).

| 그룹 | 시크릿 |
|---|---|
| Firebase | `IOS_GOOGLE_SERVICES_PLIST_B64`, `ANDROID_GOOGLE_SERVICES_JSON_B64` |
| iOS 서명 | `BUILD_CERTIFICATE_BASE64`, `BUILD_CERTIFICATE_PASSWORD`, `BUILD_PROVISION_PROFILE_BASE64`, `KEYCHAIN_PASSWORD` |
| iOS ASC | `ASC_API_KEY_ID`, `ASC_API_KEY_ISSUER_ID`, `ASC_API_KEY_CONTENT` |
| Android | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| Play | `PLAY_SERVICE_ACCOUNT_JSON_B64` |
| 모니터링 | `EXPO_PUBLIC_SENTRY_DSN` (DSN은 클라이언트 앱에 그대로 박히는 공개 식별자라 비밀값 취급 불필요하지만, 시크릿으로 넣어두면 코드/워크플로 변경 없이 값만 갈아끼울 수 있어 그렇게 함) |

- `KEYCHAIN_PASSWORD`는 임의 문자열(임시 keychain 여닫는 용).
- `.p12` 비번을 잊었으면 Keychain Access에서 "Apple Distribution..." 인증서를 새 비번으로 다시 export → `BUILD_CERTIFICATE_BASE64`/`_PASSWORD` 둘 다 갱신.
- GitHub은 저장된 시크릿 **값을 다시 안 보여준다**. 이름이 잘못 저장돼도 UI에선 티가 안 나므로(목록에서 긴 이름은 잘려 보임), 연필 아이콘으로 편집 화면을 열어 전체 이름을 확인.

### 6.5 `main`에 워크플로 올리기

`workflow_dispatch` 버튼은 기본 브랜치에 파일이 있어야 뜬다:
```bash
git push origin develop
git checkout main && git merge --ff-only develop && git push origin main
git checkout develop
```

---

## 7. 릴리스 실행 & 운영

### 커팅
```bash
git tag v1.0.0 && git push origin v1.0.0        # iOS + Android 둘 다
```
또는 Actions → **Release** → **Run workflow** → 브랜치 `main` / platform 선택.

### 빌드 후
- **iOS**: App Store Connect → TestFlight → 빌드 "처리 중" 끝나면 내부 테스터 그룹에 노출 → 실기기 스모크.
- **Android**: Play Console → 테스트 → 내부 테스트 → 최신 릴리스가 **draft** → "검토 후 출시"로 승격 → 옵트인 링크로 설치.
- 백엔드는 이 시점에만 로컬/ngrok로 기동 (상시 배포 안 함, ADR-007).

### 실패 디버깅
- 실패한 run → 실패 job → 빨간 ✗ 스텝 펼치기 → 로그 마지막 부분.
- Fastlane은 마지막에 요약 표(`| 💥 | build_app | ... |`)를 그리고 `[!]` 로 핵심 에러를 낸다. **다만 xcodebuild 에러는 잘릴 수 있음** → gym 상세 로그는 러너의 `~/Library/Logs/gym/Storia-Storia.log`. 필요하면 `release.yml`에 이 파일을 `upload-artifact`로 저장하는 스텝을 임시로 추가.
- IPA/AAB는 `if: always()`로 아티팩트에 올라가므로, 업로드 스텝만 실패했으면 빌드 산출물은 받아볼 수 있다.

### 롤백
- 이전 태그를 다시 push(빌드 번호는 계속 증가) 하거나, 스토어 콘솔에서 이전 빌드로 되돌린다.

---

## 8. 이번에 겪은 실패와 수정 (디버깅 일지)

| # | 증상 | 원인 | 수정 | 커밋 |
|---|---|---|---|---|
| 1 | Actions에 "Run workflow" 버튼 없음 | `workflow_dispatch` 워크플로가 기본 브랜치(`main`)에 없었음. `main`이 `develop`보다 한참 뒤처져 있었음 | `main`을 `develop`으로 fast-forward | — |
| 2 | `bundle` exit 16 — `empty CHECKSUMS entry for "rake" ... frozen mode` | 러너가 자동 설치한 **Bundler 4.0.15**가 스스로 만든 락파일의 CHECKSUMS를 frozen 모드에서 못 고침. `ruby/setup-ruby`의 `bundler-cache: true`가 frozen/deployment를 켬 | `BUNDLER_VERSION: "2.5.23"` 고정, `bundler-cache` 제거, 명시적 `gem install bundler -v 2.5.23 && bundle install` | `6c8b258` |
| 3 | `build_app`: `Workspace file not found ... ios/Storia.xcworkspace` | `expo prebuild`가 CI에서 `pod install`을 안 하거나 조용히 실패 → `.xcworkspace`(pod install 산출물) 없음 | `expo prebuild --no-install` + `npx pod-install` + workspace 존재 검증 스텝 | `db826d1` |
| 4 | `pod install`: `React Native requires XCode >= 16.1. Found 15.4` | `macos-14` 러너 기본 Xcode가 15.4. RN 0.86은 16.1+ 필요 | iOS job을 `macos-15`로, "Select latest Xcode" 스텝 추가 | `be79e79` |
| 5 | `build_app`: `JavaScriptCodable+Date.swift:53:50: error: type of expression is ambiguous without a type annotation` (`abs(milliseconds) <= maxJavaScriptDateMilliseconds` 줄, `expo-modules-jsi` 내부 코드) | `gh run view <id> --log-failed`로 실제 gym 로그 확보해서 확인. 러너의 최신 Xcode(26.3.0)의 Swift 6.2 컴파일러가 `expo-modules-jsi 57.0.4`의 이 표현식을 애매하다고 거부(서드파티 코드, 당장은 못 고침) | **(1차 시도, 오진)** Xcode를 16.x로 좁힘 → 관문을 하나 더 진행시켰지만 근본 해결 아니었음(#6에서 새 실패). **(최종 수정)** npm 레지스트리에서 이후 패치들을 대조해보니 `expo-modules-jsi@57.0.8`이 정확히 이 줄을 `milliseconds.magnitude <= ...`로 바꿔 고쳐놨음 — `npm update expo-modules-jsi`로 57.0.4→57.0.8(둘 다 `expo-modules-core`의 `~57.0.4` 허용 범위 안). Xcode 선택은 "최신 전체"로 되돌림(#6 참고, 16.x는애초에 옵션이 아니었음) | `64eeb20` 이후 커밋 |
| 6 | `build_app`: `xcodebuild: error: Could not resolve package dependencies: package 'apple' is using Swift tools version 6.2.0 but the installed version is 6.1.0` (#5의 "1차 시도"로 Xcode 16.4를 고른 뒤 발생) | gym 실시간 요약이 이유 줄을 또 잘라먹어서(#5와 같은 패턴) `release.yml`에 `~/Library/Logs/gym/*.log` `upload-artifact` 스텝 추가 후 재실행, 아티팩트에서 원본 로그 확인 → `expo-modules-jsi`의 `apple/Package.swift`가 `swift-tools-version: 6.2`를 선언, Swift 6.2는 Xcode 26+에만 있음 — **16.x는 애초에 옵션이 아니었다는 뜻**(#5의 1차 수정이 틀린 진단이었음을 확인) | Xcode 선택을 다시 "최신 전체"로(26.x가 필수). 26.x에서 걸리던 #5의 애매한 표현식은 57.0.8로 이미 해결돼있어 재발 안 함 | `64eeb20` 이후 커밋 |
| 7 | `build_app`: `RuntimeScheduler.h`의 `SWIFT_RETURNS_RETAINED`/`SWIFT_SHARED_REFERENCE` 페어링을 "not returning a SWIFT_SHARED_REFERENCE type"으로 거부 — 클래스가 정확히 그 매크로로 어노테이션돼있는데도 거부됨. `macos-15`가 제공하는 26.x 전부(26.0.1/26.1.1/26.2/26.3) 시도했지만 전부 실패(26.0.1/26.1.1은 이 에러 대신 `weak let` 문법 자체를 거부) | 로컬에 있는 모든 26.x Xcode로 `expo-modules-jsi`의 자체 빌드 스크립트(`apple/scripts/build-xcframework.sh`)를 직접 돌려 실측 — 26.4.0에서만 **클린 빌드 성공**. 즉 그 시점 26.2/26.3 컴파일러 자체의 버그로 확정, 우리 코드/서드파티 소스로 고칠 수 있는 게 아님. self-hosted 러너(이 Mac엔 26.4 있음)로 우회 시도까지 갔었으나, "내 컴퓨터에 원격 코드 실행 권한을 주는 게 맞나" 재고 후 전부 철회(러너 등록 해제, 로컬 파일 삭제) — 재시도 전 명시적 승인 필요로 남겨둠 | — |
| 8 | (#7 해결) 위 문제를 patch-package로 헤더 우회하려다, 먼저 리서치해보니 GitHub이 2026-02-26에 GA로 푼 **`macos-26`** 호스티드 이미지에 이미 Xcode 26.4.1/26.5/26.6이 설치돼있음을 발견(`macos-15` 이미지 문서만 보고 "26.4 ETA 불명"이라 결론 냈던 게 리서치 공백이었음) | `runs-on: macos-15` → `macos-26`, "Select latest Xcode"를 "26.4.x를 명시적으로 찾아 선택"(없으면 fail)으로 교체 — 그 이미지의 기본 latest(26.6)는 로컬에서 검증 안 된 버전이라 그대로 믿지 않음. **결과: `RuntimeScheduler` 컴파일 지점을 통과, 몇 달간 막혔던 관문 돌파** | `cab3cd2` |
| 9 | #8 배포 직후, 완전히 다른 데서 실패: `error: sentry-cli - ... An organization ID or slug is required (provide with --org)` — iOS는 `xcodebuild archive` 중 "Bundle React Native code and images" 스텝에서, Android는 `:app:createBundleReleaseJsAndAssets_SentryUpload...` Gradle 태스크에서 **똑같은 에러**로 실패 | `@sentry/react-native`가 심어둔 빌드 스크립트가 Sentry org/auth token 없이 소스맵·디버그심볼을 업로드하려다 실패. 로컬 `expo run:ios`는 이미 `SENTRY_DISABLE_AUTO_UPLOAD=true`로 이걸 피해가고 있었는데, CI(`release.yml`)엔 이 env var가 아예 없었음 | 두 job env에 `SENTRY_DISABLE_AUTO_UPLOAD: "true"` 추가 | iOS `2e818ae`, Android `87482e5` |
| 10 | (기능 추가, 실패 아님) Sentry DSN이 없어서 크래시 리포팅이 그동안 완전히 비활성 상태였음 | — | Sentry 프로젝트 생성(org `ikerstory`, project `storia-client`, React Native 플랫폼) → DSN을 `EXPO_PUBLIC_SENTRY_DSN` GitHub Secret으로 등록 → 두 job env에 주입. 소스맵 자동 업로드(auth token 필요)는 아직 안 함 — `SENTRY_DISABLE_AUTO_UPLOAD`는 유지, 크래시 이벤트 자체는 이제 수신됨 | `e4159c1` |

**결과 (2026-09-11)**: `platform: ios` workflow_dispatch 그린 — `Storia.ipa` 아티팩트 생성 + TestFlight에 빌드 1.0.0 (18) "처리 중"으로 업로드 성공. **iOS 릴리스 파이프라인 첫 완주.**

**패턴**: 파이프라인은 한 관문씩 뚫린다 — 브랜치 → bundler → pod install → Xcode → 실제 컴파일/서명 → (이번엔) Sentry 빌드 스크립트. 각 실패는 다음 관문을 드러낸다. **교훈 1**: 러너에 여러 Xcode가 같이 있을 때 실패를 "버전을 낮춰서" 피하고 싶어지지만, 다른 의존성이 그 신형 버전을 실제로 요구하는 경우(`swift-tools-version` 등)가 있으니 버전을 좁히기 전에 왜 최신이 필요한지부터 확인할 것 — 여기선 되돌아가는 삽질 한 번(#5→#6)을 했음. **교훈 2**: gym의 실시간 요약은 다줄짜리 `xcodebuild` 에러 메시지를 반복적으로 잘라먹으므로, 서명/컴파일 실패를 디버깅할 땐 애초에 gym 원본 로그를 아티팩트로 남기고 시작할 것 — fastlane 요약만 보고 추측하지 말 것. **교훈 3**: "호스티드 러너 중엔 필요한 버전이 없다"는 결론은 확인한 러너 이미지 하나(`macos-15`)에만 해당하는 얘기일 수 있다 — GitHub은 macOS 메이저 버전마다 별도 이미지(`macos-26` 등)를 내는데, 오래된 이미지만 보고 "전체 호스티드 환경에 없다"로 성급히 일반화하면 self-hosted 같은 훨씬 무거운 우회로를 괜히 먼저 시도하게 된다 — 다른 이미지 라인업부터 확인할 것. **교훈 4**: 로컬 개발 환경에서만 필요했던 env var(`SENTRY_DISABLE_AUTO_UPLOAD` 등)는 CI가 로컬과 다른 셸 환경이라는 이유만으로 누락되기 쉽다 — 새 외부 SDK를 도입할 때 로컬용 `.env`/실행 커맨드에 넣은 값은 CI 워크플로에도 대응 항목이 있는지 항상 같이 체크할 것.

---

## 9. 비용

| 항목 | 비용 |
|---|---|
| GitHub Actions (ubuntu + **macOS** 러너) | **무료** — public 저장소는 무제한 |
| Fastlane | 무료 (오픈소스) |
| `storia-certificates` private repo | 안 씀 (`.p12` 직접 방식) |
| Apple Developer Program | $99/년 (이미 보유) |
| Google Play Console | $25 1회 (이미 보유) |

> private 저장소였다면 macOS 러너가 무료 크레딧을 10배 배율로 소모한다. Storia는 public이라 무관.

---

## 10. 관련 파일

```
.github/workflows/
  ci.yml                         # 테스트 게이트
  release.yml                    # 릴리스 CD
apps/client/
  Gemfile                        # fastlane ~> 2.223
  fastlane/
    Fastfile                     # ios beta / android beta lane
    Appfile                      # app_identifier, apple_id, team_id
    Pluginfile
  plugins/
    withAndroidReleaseSigning.js # prebuild 시 release signingConfig 주입
docs/
  cicd.md                        # (이 문서)
  deployment.md                  # 시크릿 표 + 1회성 계정 셋업
  decisions.md                   # ADR-008 (EAS → Fastlane 결정)
  testing.md                     # 테스트 스위트 상세
```
