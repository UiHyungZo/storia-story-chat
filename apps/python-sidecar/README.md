# python-sidecar

TODO.md 5주차 "완전한 A안(원래 정의의 양방향 실시간 음성) 확장" 항목의 스켈레톤 구현.
`docs/decisions.md` ADR-004, `HANDOFF.md`도 함께 참고.

## 왜 별도 프로세스인가

Spring Boot(JVM)에는 "서버가 라이브 LiveKit 세션에 오디오를 다시 publish"하는 표준
경로가 없다. LiveKit도 이 문제를 Python/Node **Agents SDK**로 처리하는 걸 표준으로
삼고 있어서, Spring 안에 "붙이는" 대신 **별도 프로세스로 띄우고 REST로 통신**하는
사이드카 패턴을 택했다.

**Spring 백엔드 코드는 거의 바뀌지 않는다.** 이 사이드카는 캐릭터 시스템 프롬프트
조회, Gemini 호출, 메시지 저장, FCM 푸시를 새로 구현하지 않고, 모바일 클라이언트가
WebSocket 실패 시 쓰는 것과 **동일한 REST 폴백 엔드포인트**
(`POST /api/conversations/{characterId}/messages`, `ConversationController.java`)를
그대로 호출한다 — 로직 중복 없음.

예외가 하나 있다: `VoiceCallService.createToken()`이 발급하는 클라이언트 토큰이
원래 `CanSubscribe(false)`였는데, 이 에이전트가 room에 TTS 오디오 트랙을 publish해도
클라이언트가 구독 권한이 없으면 못 듣는다. 그래서 `CanSubscribe(true)`로 바꿔야
했다(이미 반영함) — 이건 새 비즈니스 로직이 아니라 권한 플래그 하나를 여는 것뿐이다.

## 기존 "축소판 A안"과의 관계

`VoiceCallService`/`VoiceEgressWebSocketHandler`(Track Egress → raw WebSocket →
배치 STT/Gemini/TTS → 오디오 URL 반환) 경로는 그대로 둔다. 이 사이드카는 그걸
대체하는 게 아니라 **추가되는 대안 경로**다 — LiveKit Agent 워커가 떠 있으면 자동으로
새 room에 들어와 실시간으로 응답하고, 안 떠 있으면 기존 축소판 경로만 동작한다.

## 구성

- `agent.py` — LiveKit Agents SDK 워커. room에 봇으로 들어가 유저 오디오를 STT →
  (Spring REST 호출로 응답 텍스트 획득) → TTS → 그 세션에 오디오로 되쏜다.
- `storia_client.py` — Spring 백엔드 REST 호출 얇은 래퍼.

## 실행

**Python 3.10~3.14 필요**(`livekit-agents`가 `>=3.10,<3.15` 요구 — 이 머신 기본 `python3`는
3.9라 `brew install python@3.12`로 별도 설치):

```bash
cd apps/python-sidecar
python3.12 -m venv .venv && source .venv/bin/activate   # /opt/homebrew/bin/python3.12
pip install -r requirements.txt
cp .env.example .env   # LIVEKIT_*, STORIA_BACKEND_URL, GOOGLE_APPLICATION_CREDENTIALS 채우기
python agent.py start          # 워커(automatic dispatch). 개발 중엔 `dev`
```

`GOOGLE_APPLICATION_CREDENTIALS`는 백엔드의 `STT_API_KEY`/`TTS_API_KEY`(단순 API 키)와
**다른 인증** — GCP 콘솔에서 서비스 계정("Cloud Speech-to-Text 클라이언트" 역할)을
하나 만들어 JSON 키를 받고 **리포 밖**에 둔다.

## 검증 상태

**(2026-08-31 실제 왕복 검증)** LiveKit Cloud 프로젝트 + Google 서비스 계정으로
`livekit-agents==1.7.1` / `livekit-plugins-google==1.7.1` 환경에서 실제 room에 붙여 확인:

- ✅ `pip install -r requirements.txt` 정상 설치, `agent.py` API 시그니처를 1.7.1 기준으로
  재확인(`Agent.llm_node`, `ChatMessage.text_content`, `JobContext.add_shutdown_callback`,
  `google.STT/TTS` 인자).
- ✅ **automatic dispatch** — `agent_name` 미설정 워커가 `call-{deviceId}-{characterId}`
  형태의 ad-hoc room에 별도 dispatch 룰 없이 자동 진입함(`received job request`).
- ✅ **실시간 STT** — `livekit-plugins-google` STT가 유저 오디오를 한국어로 전사
  (`received user transcript`), 턴 감지(`user turn committed`)까지 동작.
- ✅ **Spring 위임** — `StoriaLLM`이 `POST /api/conversations/{id}/messages`를 호출해
  캐릭터 응답을 받아옴. DB에 유저/어시스턴트 메시지가 정상 저장되고 응답이 캐릭터
  페르소나 그대로 옴(= system prompt / Gemini / 영속화 / FCM 로직 중복 없음).
- ✅ **TTS 되쏘기** — 에이전트가 응답 텍스트를 합성해 room에 오디오 트랙으로 publish,
  구독자(테스트 클라이언트)가 실제 오디오 프레임 수신.
- ⚠️ **에이전트 음성의 깨끗한 재생 확인은 미완** — 합성 파이썬 테스트 클라이언트
  (`rtc.AudioStream` 구독+리샘플)로 캡처한 오디오는 리샘플 아티팩트로 역-전사가 안 됨.
  축소판 A안의 RN 클라이언트 재생 검증과 동일하게 **실제 RN 앱으로 확인 필요**.

**1.7.x에서 새로 맞춰야 했던 3가지 (agent.py 반영):**

1. **`AgentSession(llm=...)`가 없으면 응답 생성 자체를 건너뜀** — `AgentActivity`에
   `elif self.llm is None: return  # skip response if no llm is set`가 있음. Spring
   위임을 `Agent.llm_node` 오버라이드가 아니라 `llm.LLM`/`llm.LLMStream` 구현
   (`StoriaLLM`/`StoriaLLMStream`)으로 넣고 `AgentSession(llm=StoriaLLM(...))`로 전달.
2. **워커 헬스체크 HTTP 포트 8081이 Metro(apps/client)와 충돌** →
   `WorkerOptions(port=8083)`.
3. **Google Cloud TTS 스트리밍은 Chirp 3 HD 보이스만 지원** (`ko-KR-Standard-*`는
   `INVALID_ARGUMENT: only Chirp 3: HD voices ... for streaming synthesis`) →
   `google.TTS(voice_name="ko-KR-Standard-A", use_streaming=False)`로 배치 합성 사용
   (백엔드 `TtsService`와 같은 보이스 계열).

## 다음에 확인할 것

- 실제 RN 앱(`useVoiceCallStore` — 이미 `CanSubscribe(true)` 토큰을 받으므로 에이전트
  오디오 구독 가능)으로 에이전트 음성이 실제로 들리는지, 축소판 경로와의 전환/공존.
- `use_streaming=False` 배치 TTS의 긴 응답 재생이 끝까지 나가는지(테스트 하네스에선
  ~5초 분량만 캡처됨 — 하네스 한계인지 실제 truncation인지 실기기로 재확인).
- 배포 파이프라인에 이 런타임(Python) 추가 방법(Docker 이미지, 프로세스 관리) —
  TODO.md 7주차 항목.
