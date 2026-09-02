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
| **Release (CD)** | `.github/workflows/release.yml` | 태그 `v*` push, 또는 수동 실행 | iOS→TestFlight / Android→Play 내부 테스트 | iOS: `macos-15`, Android: `ubuntu-latest` |

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

## 4. iOS job 단계별 (`macos-15`)

| # | 스텝 | 하는 일 | 왜 |
|---|---|---|---|
| 1 | `actions/checkout` | 소스 체크아웃 | |
| 2 | **Select latest Xcode** | `ls /Applications/Xcode*.app \| sort -V \| tail -1` → `sudo xcode-select -s` | 러너 기본 Xcode가 낮을 수 있음. RN 0.86은 **Xcode ≥ 16.1** 필요 |
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

### 6.4 GitHub Secrets 14개

`base64 -i <파일> | pbcopy` 로 파일을 base64로 만들어 붙여넣는다. `pbcopy < 파일.txt` 방식이 복사 실수가 없다. 전체 표는 [`deployment.md`](./deployment.md).

| 그룹 | 시크릿 |
|---|---|
| Firebase | `IOS_GOOGLE_SERVICES_PLIST_B64`, `ANDROID_GOOGLE_SERVICES_JSON_B64` |
| iOS 서명 | `BUILD_CERTIFICATE_BASE64`, `BUILD_CERTIFICATE_PASSWORD`, `BUILD_PROVISION_PROFILE_BASE64`, `KEYCHAIN_PASSWORD` |
| iOS ASC | `ASC_API_KEY_ID`, `ASC_API_KEY_ISSUER_ID`, `ASC_API_KEY_CONTENT` |
| Android | `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| Play | `PLAY_SERVICE_ACCOUNT_JSON_B64` |

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
| 5 | *(미해결)* `build_app`(xcodebuild archive) 실패 ~79초, 실제 에러가 fastlane 요약에서 잘림 | 조사 중 — gym 상세 로그 확보 필요 | 다음 세션 | — |

**패턴**: 파이프라인은 한 관문씩 뚫린다 — 브랜치 → bundler → pod install → Xcode → (현재) 실제 컴파일/서명. 각 실패는 다음 관문을 드러낸다.

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
