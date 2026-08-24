# TODO

PRD v3([`PRD/Storia_PRD_v3.md`](./PRD/Storia_PRD_v3.md)) 마일스톤 기준. 완료된 항목은 커밋 로그 참고, 여기는 남은 작업만 추적.

## 지금 당장 (1주차 마무리 갭)

- [x] 클라이언트에서 실제 백엔드 REST API 호출로 전환 (`GET /api/characters`, `GET /api/conversations/{characterId}/messages`) — `dummyCharacters.ts` 제거, `src/api/` 계층 추가
- [x] 클라이언트 디바이스 ID 생성 및 영속화 (AsyncStorage) — `src/api/deviceId.ts`, `X-Device-Id` 헤더로 전송
- [x] 메시지 전송용 REST 엔드포인트 추가 (`POST /api/conversations/{characterId}/messages`) — `ConversationService#postMessage` + 컨트롤러 매핑
- [x] 백엔드 CORS 설정 — `WebConfig`(`/api/**` 전체 허용, 로컬 개발 전용)
- [ ] **아직 코드만 작성, 실기기/에뮬레이터 확인 안 됨** — 다음 세션에서 docker compose up → gradlew bootRun → expo start로 실제 동작 검증 필요 (아래 "검증 필요" 참고)

### 검증 필요 (다음 세션, 로컬 실행 후)

- [ ] Android 에뮬레이터는 `10.0.2.2`, iOS 시뮬레이터는 `localhost`로 자동 분기(`src/api/config.ts`) — 실기기 테스트 시 `EXPO_PUBLIC_API_BASE_URL` 환경변수로 개발 머신 LAN IP를 넣어야 함 (자동 감지 불가)
- [ ] 캐릭터 목록 → 채팅방 진입 → 메시지 전송 → 새로고침 후 히스토리 복원까지 실제 왕복 확인
- [ ] CORS 설정이 실제로 필요했는지(RN 네이티브는 CORS 영향 없음, Expo 웹/브라우저 디버깅 시나리오만 해당) 확인 후 불필요하면 제거 검토

## 2주차 — Gemini 연동 & WebSocket

- [x] Google Gemini API 연동 (백엔드, 텍스트 스트리밍) — `GeminiService`(WebClient + SSE), `GEMINI_API_KEY` 환경변수 필요
- [x] WebSocket(STOMP) 채널 구축 — `/app/conversation/{characterId}/send` → `/topic/conversation/{characterId}` (`WebSocketConfig`, `ConversationStompController`)
- [x] 클라이언트 WebSocket 클라이언트 연동 + 스트리밍 청크 렌더링(타이핑 효과) — `src/api/websocket.ts`, `useConversationStore`의 `streamingByCharacterId`
- [x] REST 폴백 경로 구현 (WebSocket 실패 시) — `POST /api/conversations/{characterId}/messages`도 Gemini를 동기 호출해 (논스트리밍) 응답을 반환하도록 변경, 클라이언트는 캐릭터별로 화면 진입 시 WS 연결을 1회 시도하고 실패하면 그 세션 동안 REST로 전환
- [ ] **아직 코드만 작성, 실행 검증 안 됨** — 아래 "검증 필요" 참고

### 검증 필요 (다음 세션, 로컬 실행 후 — 2주차)

- [ ] `GEMINI_API_KEY` 환경변수 발급/설정 후 `gradlew bootRun` — 키 없으면 WS는 ERROR 이벤트, REST는 고정 안내 문구로 폴백하는지 확인
- [ ] 채팅방 진입 → 메시지 전송 → 청크가 실시간으로 쌓여 타이핑 효과가 보이는지, 완료 시 최종 메시지로 치환되는지 확인
- [ ] 백엔드를 내려서 강제로 WS 연결 실패 상황을 만든 뒤 REST 폴백(전체 응답 한 번에 반환)이 정상 동작하는지 확인
- [ ] `@stomp/stompjs`가 RN(Hermes) 환경에서 별도 폴리필 없이 붙는지 확인 — 문제 있으면 `TextEncoder`/`TextDecoder` 폴리필 필요할 수 있음
- [ ] 현재는 화면 진입 시 WS 연결을 1회만 시도하고 세션 내내 그 결과(ws/rest)를 유지함 — 스트리밍 도중 연결이 끊기는 시나리오의 재시도/재연결은 3주차 범위로 남겨둠

## 3주차 — 안정성 & 동기화

- [ ] 로딩/오류/재시도 UI
- [ ] WebSocket 재연결 로직
- [ ] DB 히스토리 저장/복원 검증
- [ ] 로컬 캐시(AsyncStorage) 동기화

## 4주차 — Native Module & 푸시

- [ ] Swift Native Module (Haptic + 로컬 알림)
- [ ] FCM 원격 푸시 (백엔드 Admin SDK)

## 5주차 — 음성 통화 B안

- [ ] RN STT(`@react-native-voice/voice`) 연동
- [ ] 서버 TTS 연동 (Google Cloud TTS 또는 ElevenLabs 무료 티어)
- [ ] 오디오 파일 URL 응답 → 클라이언트 재생 파이프라인

## 6주차 — WebRTC 최소 데모 (C안)

- [ ] WebRTC 시그널링 서버 (Offer/Answer/ICE, WebSocket 채널로 중계)
- [ ] `react-native-webrtc` 1:1 P2P 오디오 스트리밍 최소 데모
- [ ] B안과의 통합 여부 판단 (시간 여유 시 A안 확장 검토)

## 7주차 — 모니터링 & 배포

- [ ] Sentry 연동 (클라이언트 RN SDK + 백엔드 Spring Boot SDK)
- [ ] 에러 시나리오 검증 (WebRTC 연결 실패 포함)
- [ ] 테스트 코드 (Jest+RNTL / JUnit5+Spring Boot Test) — 현재 `BackendApplicationTests`만 존재, 실질 테스트 없음
- [ ] Docker/Fastlane/GitHub Actions 배포 파이프라인
- [ ] iOS(TestFlight)/Android(Firebase App Distribution)/백엔드 배포

## 문서화 (진행 중 계속 갱신)

- [ ] API 명세 문서 (`docs/api/` — Swagger로 충분한지, 별도 정리 필요한지 판단)
- [ ] B안/C안 선택 이유 기술 블로그 초안 (PRD 7절 포트폴리오 활용 방안 참고)
