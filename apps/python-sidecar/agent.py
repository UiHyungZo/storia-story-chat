"""LiveKit Agents SDK 기반 실시간 음성 에이전트 (TODO.md 5주차 "완전한 A안" 확장).

기존 "축소판 A안"(VoiceCallService/VoiceEgressWebSocketHandler)은 Track Egress로
받은 오디오를 배치로 STT→Gemini→TTS 처리해 오디오 URL로 응답한다. 이 사이드카는
그 대신 LiveKit Agents SDK로 room에 봇처럼 들어가서 유저 오디오를 실시간 구독하고,
합성한 TTS 오디오를 그 세션에 다시 publish까지 한다 — "서버가 라이브 세션에 오디오
되쏘기"를 Agents SDK가 대신 해결해준다.

**두뇌(LLM 호출) 로직은 새로 만들지 않는다.** system prompt 조회, 메시지 영속화,
FCM 푸시는 전부 기존 Spring Boot 백엔드가 갖고 있으므로, `llm_node`에서 실제 LLM을
부르는 대신 Spring의 기존 REST 엔드포인트(`storia_client.StoriaBackendClient`)를
그대로 호출한다.

(2026-08-26) `pip install -r requirements.txt`가 Python 3.12 + `livekit-agents==1.3.12`
기준으로 실제 설치되는 것과, `ChatContext`/`llm_node`/`JobContext.add_shutdown_callback`
API 시그니처는 설치된 패키지를 직접 introspect해서 확인·반영함(아래 각 지점 주석 참고).
여전히 미검증: LiveKit/Google 자격증명이 없어 실제로 room에 연결해 대화를 나눠본 적은
없음 — automatic dispatch 동작, STT/TTS 플러그인 실제 왕복은 그대로 남아있는 항목.
"""

from __future__ import annotations

import logging
import os

from dotenv import load_dotenv
from livekit.agents import Agent, AgentSession, JobContext, WorkerOptions, cli, llm
from livekit.agents.llm import ChatMessage
from livekit.plugins import google, silero

from storia_client import StoriaBackendClient

load_dotenv()

logger = logging.getLogger("storia-voice-agent")

STORIA_BACKEND_URL = os.environ.get("STORIA_BACKEND_URL", "http://localhost:8080")


def parse_room_name(room_name: str) -> tuple[str, int]:
    """`VoiceCallService.roomNameFor()`가 만드는 "call-{deviceId}-{characterId}" 파싱.

    deviceId(UUID)는 하이픈을 포함할 수 있지만 characterId는 순수 숫자이므로,
    오른쪽에서 한 번만 잘라야 안전하다.
    """
    body = room_name.removeprefix("call-")
    device_id, _, character_id = body.rpartition("-")
    return device_id, int(character_id)


class StoriaAgent(Agent):
    """STT/TTS는 LiveKit 플러그인이 실시간 처리하고, 응답 생성은 Spring 백엔드에 위임."""

    def __init__(self, backend: StoriaBackendClient, device_id: str, character_id: int) -> None:
        # instructions는 비워둔다 — 캐릭터 시스템 프롬프트는 이미 Spring/DB가 갖고 있고,
        # 실제 생성도 Spring이 하므로 이 프로세스는 프롬프트를 알 필요가 없다.
        super().__init__(instructions="")
        self._backend = backend
        self._device_id = device_id
        self._character_id = character_id

    async def llm_node(self, chat_ctx: llm.ChatContext, tools, model_settings=None):
        # (2026-08-26 검증) chat_ctx.items는 ChatMessage 외에도 FunctionCall/
        # FunctionCallOutput/AgentHandoff 등이 섞인 유니온(ChatItem)이라 마지막 원소가
        # 항상 유저 발화 ChatMessage라는 보장이 없음 — role="user"인 ChatMessage를
        # 뒤에서부터 찾아야 함. text_content는 ChatMessage에만 있는 프로퍼티(1.3.x 기준
        # livekit/agents/llm/chat_context.py에서 직접 확인).
        user_text = ""
        for item in reversed(chat_ctx.items):
            if isinstance(item, ChatMessage) and item.role == "user":
                user_text = item.text_content or ""
                break
        if not user_text:
            yield "죄송해요, 잘 못 들었어요."
            return
        reply = await self._backend.send_message(self._device_id, self._character_id, user_text)
        yield reply


async def entrypoint(ctx: JobContext) -> None:
    await ctx.connect()

    device_id, character_id = parse_room_name(ctx.room.name)
    backend = StoriaBackendClient(STORIA_BACKEND_URL)

    # STT/TTS는 Google Cloud 플러그인 사용 — 단, 이건 Spring의 STT_API_KEY/TTS_API_KEY
    # (단순 API 키 방식)와 인증 모델이 다르다. livekit-plugins-google은 서비스 계정
    # JSON(GOOGLE_APPLICATION_CREDENTIALS)을 요구하므로, 같은 GCP 프로젝트라도 별도로
    # 서비스 계정을 하나 더 발급받아야 한다. README 참고.
    session = AgentSession(
        vad=silero.VAD.load(),
        stt=google.STT(languages="ko-KR"),
        tts=google.TTS(language="ko-KR", voice_name="ko-KR-Standard-A"),
    )

    # session.start()는 세션을 시작만 시키고 리턴하며, 대화는 잡 프로세스가 살아있는
    # 동안(참가자 퇴장 등으로 잡이 끝날 때까지) 백그라운드에서 계속된다 — 그래서 여기서
    # 곧바로 backend.aclose()를 부르면 안 된다. (2026-08-26 검증) JobContext에
    # add_shutdown_callback(callback)이 실제로 존재함 — 인자 없는 콜백/이유 문자열을
    # 받는 콜백 둘 다 허용(livekit/agents/job.py에서 직접 확인). 잡이 끝날 때(참가자 전원
    # 퇴장 등) httpx 클라이언트를 정리하도록 등록.
    ctx.add_shutdown_callback(backend.aclose)
    await session.start(agent=StoriaAgent(backend, device_id, character_id), room=ctx.room)


if __name__ == "__main__":
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint))
