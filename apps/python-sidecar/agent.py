"""LiveKit Agents SDK 기반 실시간 음성 에이전트 (TODO.md 5주차 "완전한 A안" 확장).

기존 "축소판 A안"(VoiceCallService/VoiceEgressWebSocketHandler)은 Track Egress로
받은 오디오를 배치로 STT→Gemini→TTS 처리해 오디오 URL로 응답한다. 이 사이드카는
그 대신 LiveKit Agents SDK로 room에 봇처럼 들어가서 유저 오디오를 실시간 구독하고,
합성한 TTS 오디오를 그 세션에 다시 publish까지 한다 — "서버가 라이브 세션에 오디오
되쏘기"를 Agents SDK가 대신 해결해준다.

**두뇌(LLM 호출) 로직은 새로 만들지 않는다.** 캐릭터 시스템 프롬프트 조회, 메시지
영속화, FCM 푸시는 전부 기존 Spring Boot 백엔드가 갖고 있으므로, 실제 LLM을 부르는
대신 `StoriaLLM`이 Spring의 기존 REST 엔드포인트(`storia_client.StoriaBackendClient`)를
그대로 호출해 응답 텍스트만 받아온다. LiveKit이 그 텍스트를 STT/TTS 파이프라인의
LLM 단계 출력으로 취급하므로 나머지(턴 감지, TTS, room publish)는 그대로 흘러간다.

(2026-08-31) LiveKit Cloud + Google 서비스 계정으로 실제 room에 붙여 검증:
automatic dispatch로 `call-*` room에 자동 진입, Google STT 실시간 전사, `StoriaLLM`이
Spring `/api/conversations/{id}/messages`를 호출해 캐릭터 응답을 받아 TTS로 되쏘는
왕복까지 확인함. `livekit-agents==1.7.x`에서는 `AgentSession(llm=...)`이 없으면
`AgentActivity`가 "skip response if no llm is set"로 응답 생성 자체를 건너뛰므로,
Spring 위임 로직을 `Agent.llm_node` 오버라이드가 아니라 `llm.LLM` 구현으로 넣어야 한다.
"""

from __future__ import annotations

import logging
import os
import uuid

from dotenv import load_dotenv
from google.cloud import texttospeech
from livekit.agents import Agent, AgentSession, JobContext, WorkerOptions, cli, llm
from livekit.agents.llm import ChatMessage
from livekit.agents.types import DEFAULT_API_CONNECT_OPTIONS, APIConnectOptions
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


class StoriaLLMStream(llm.LLMStream):
    """한 번의 응답 생성: 마지막 유저 발화를 Spring에 보내고 그 응답 텍스트를 청크 하나로 흘린다."""

    def __init__(
        self,
        llm: StoriaLLM,
        *,
        chat_ctx: llm.ChatContext,
        tools,
        conn_options: APIConnectOptions,
        backend: StoriaBackendClient,
        device_id: str,
        character_id: int,
    ) -> None:
        super().__init__(llm, chat_ctx=chat_ctx, tools=tools, conn_options=conn_options)
        self._backend = backend
        self._device_id = device_id
        self._character_id = character_id

    async def _run(self) -> None:
        # chat_ctx.items는 ChatMessage 외에 FunctionCall/FunctionCallOutput 등이 섞인
        # 유니온이라 마지막 원소가 항상 유저 발화 ChatMessage라는 보장이 없음 —
        # role="user"인 ChatMessage를 뒤에서부터 찾는다. text_content는 ChatMessage 전용.
        user_text = ""
        for item in reversed(self._chat_ctx.items):
            if isinstance(item, ChatMessage) and item.role == "user":
                user_text = item.text_content or ""
                break

        if not user_text:
            reply = "죄송해요, 잘 못 들었어요."
        else:
            reply = await self._backend.send_message(self._device_id, self._character_id, user_text)

        self._event_ch.send_nowait(
            llm.ChatChunk(
                id=str(uuid.uuid4()),
                delta=llm.ChoiceDelta(role="assistant", content=reply),
            )
        )


class StoriaLLM(llm.LLM):
    """LLM을 실제로 호출하지 않는 LLM 노드 — 유저 발화를 기존 Spring REST에 위임하고
    그 응답을 스트림으로 돌려준다. 캐릭터 프롬프트/Gemini 호출/메시지 저장/FCM 푸시가
    전부 Spring 한 곳에 남으므로 두 런타임 사이 로직 중복이 없다."""

    def __init__(self, backend: StoriaBackendClient, device_id: str, character_id: int) -> None:
        super().__init__()
        self._backend = backend
        self._device_id = device_id
        self._character_id = character_id

    def chat(
        self,
        *,
        chat_ctx,
        tools=None,
        conn_options: APIConnectOptions = DEFAULT_API_CONNECT_OPTIONS,
        **kwargs,
    ) -> StoriaLLMStream:
        return StoriaLLMStream(
            self,
            chat_ctx=chat_ctx,
            tools=tools or [],
            conn_options=conn_options,
            backend=self._backend,
            device_id=self._device_id,
            character_id=self._character_id,
        )


class StoriaAgent(Agent):
    def __init__(self) -> None:
        # 캐릭터 시스템 프롬프트는 Spring/DB가 갖고 있고 응답 생성도 Spring이 하므로
        # (StoriaLLM 참고) 이 에이전트는 자체 instructions가 필요 없다.
        super().__init__(instructions="")


async def entrypoint(ctx: JobContext) -> None:
    await ctx.connect()

    device_id, character_id = parse_room_name(ctx.room.name)
    backend = StoriaBackendClient(STORIA_BACKEND_URL)

    # STT/TTS는 Google Cloud 플러그인 사용 — Spring의 STT_API_KEY/TTS_API_KEY(단순 API
    # 키)와 달리 서비스 계정 JSON(GOOGLE_APPLICATION_CREDENTIALS)을 요구한다. README 참고.
    session = AgentSession(
        llm=StoriaLLM(backend, device_id, character_id),
        vad=silero.VAD.load(),
        stt=google.STT(languages="ko-KR"),
        # livekit-plugins-google's google.TTS is the Gemini/Chirp plugin, NOT classic
        # Cloud TTS. `ko-KR-Standard-*` is not a valid voice there — it silently routes
        # to gemini-2.5-flash-tts (which needs "Agent Platform API" enabled, and we
        # haven't) and the client hears static. Chirp 3 HD is Google's real-time-grade
        # TTS and works, but only via batch (`use_streaming=False`) and only if we ask
        # for LINEAR16 — the plugin's default PCM encoding and its streaming path both
        # get a 400 from Chirp voices. 48 kHz matches LiveKit's native rate.
        tts=google.TTS(
            language="ko-KR",
            voice_name="ko-KR-Chirp3-HD-Charon",
            audio_encoding=texttospeech.AudioEncoding.LINEAR16,
            sample_rate=48000,
            use_streaming=False,
        ),
    )

    # session.start()는 세션을 시작만 시키고 리턴하며, 대화는 잡 프로세스가 살아있는
    # 동안(참가자 퇴장 등으로 잡이 끝날 때까지) 백그라운드에서 계속된다 — 그래서 여기서
    # 곧바로 backend.aclose()를 부르면 안 된다. JobContext.add_shutdown_callback으로
    # 잡이 끝날 때 httpx 클라이언트를 정리하도록 등록한다(인자 없는 콜백 허용).
    ctx.add_shutdown_callback(backend.aclose)
    await session.start(agent=StoriaAgent(), room=ctx.room)


if __name__ == "__main__":
    # The worker runs a health-check HTTP server; its default port 8081 collides with
    # the Expo/Metro dev server (apps/client) and 8080 is the Spring backend, so move it.
    cli.run_app(WorkerOptions(entrypoint_fnc=entrypoint, port=8083))
