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

## 실행 (미검증)

```bash
cd apps/python-sidecar
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # 값 채우기
python agent.py dev
```

## 미검증 항목 / 다음에 확인할 것

이 머신엔 LiveKit/Google 자격증명이 없어서(다른 apps/backend 항목들과 마찬가지로)
아래는 전부 **코드만 작성했고 실제로 돌려본 적이 없다**:

- `pip install`이 실제로 되는지, `requirements.txt`의 패키지/버전명이 정확한지
- `agent.py`의 `llm_node(chat_ctx, tools, model_settings)` 시그니처와
  `chat_ctx.items[-1].text_content`로 최근 유저 발화를 꺼내는 방법 — 설치되는
  `livekit-agents` 버전의 실제 `ChatContext` API와 대조 필요
- room 종료/참가자 퇴장 시 `StoriaBackendClient`를 정리하는 정확한 훅(잡 라이프사이클
  콜백) — 지금은 아예 안 닫음
- LiveKit Agent 워커의 기본 automatic dispatch가 `call-{deviceId}-{characterId}`
  형태의 ad-hoc room에도 그대로 붙는지, 아니면 explicit dispatch 룰이 필요한지
- `livekit-plugins-google`이 요구하는 서비스 계정 발급 및 `GOOGLE_APPLICATION_CREDENTIALS`
  설정
- 배포 파이프라인에 이 런타임(Python)을 어떻게 추가할지(Docker 이미지, 프로세스
  관리) — TODO.md 7주차 항목과 연결
