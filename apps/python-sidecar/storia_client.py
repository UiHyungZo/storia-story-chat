"""Spring Boot 백엔드 REST 클라이언트.

이 사이드카는 Gemini 호출이나 DB 접근을 직접 하지 않는다. 시스템 프롬프트 조회,
메시지 영속화, FCM 푸시 같은 비즈니스 로직은 전부 기존 Spring Boot 백엔드에
남겨두고, 모바일 클라이언트가 WebSocket 실패 시 쓰는 것과 동일한 REST 폴백
엔드포인트(`ConversationController.postMessage`)를 그대로 호출한다. 이렇게 하면
두 런타임 사이에 로직 중복이 생기지 않는다 (TODO.md 5주차 "완전한 A안" 항목 참고).
"""

from __future__ import annotations

import httpx


class StoriaBackendClient:
    # POST /api/conversations/{id}/messages calls Gemini synchronously on the Spring
    # side. gemini-3.6-flash is a reasoning model that regularly takes 30-46s on a
    # character prompt (GeminiService.CHUNK_TIMEOUT is 60s; the RN reduced-A-plan
    # client polls for 75s for the same reason), so a 30s HTTP read timeout here
    # aborts the happy path and the agent never gets a reply to speak.
    def __init__(self, base_url: str, timeout: float = 90.0) -> None:
        self._client = httpx.AsyncClient(base_url=base_url, timeout=timeout)

    async def send_message(self, device_id: str, character_id: int, content: str) -> str:
        """유저 발화 텍스트를 보내고 어시스턴트 응답 텍스트를 받는다.

        내부적으로 메시지 저장(유저+어시스턴트)과 Gemini 호출, FCM 푸시까지
        Spring 쪽에서 전부 처리된다 — 여기서는 결과 텍스트만 꺼내 쓴다.
        """
        response = await self._client.post(
            f"/api/conversations/{character_id}/messages",
            headers={"X-Device-Id": device_id},
            json={"content": content},
        )
        response.raise_for_status()
        return response.json()["assistantMessage"]["content"]

    async def aclose(self) -> None:
        await self._client.aclose()
