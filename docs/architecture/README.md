# 아키텍처

## 현재 구현 상태 (Week 1)

REST API 스켈레톤만 연결되어 있습니다. WebSocket 스트리밍, Gemini 연동, WebRTC 음성 통화는 아직 구현되지 않았습니다 (PRD v3 마일스톤 2주차 이후).

```mermaid
flowchart LR
    Client["RN Client\n(Expo)"] -->|"GET /api/characters"| Backend["Spring Boot\nBackend"]
    Client -->|"GET /api/conversations/:id/messages"| Backend
    Backend --> DB[("MariaDB")]
```

## 목표 아키텍처 (PRD v3 최종 형태)

텍스트는 WebSocket(STOMP)으로 Gemini 스트리밍을 중계하고, 음성 통화는 WebRTC로 Signaling을 처리한 뒤 STT → LLM → TTS 파이프라인을 거칩니다. 자세한 내용은 [`PRD/Storia_PRD_v3.md`](../../PRD/Storia_PRD_v3.md) 3.3, 3.9 참고.

```mermaid
flowchart LR
    subgraph Client["RN Client (Expo)"]
        Chat["채팅방 UI"]
        Call["음성 통화 UI"]
    end

    subgraph Backend["Spring Boot Backend"]
        WS["WebSocket(STOMP)\n/topic/conversation/:id"]
        Signal["WebRTC Signaling\n(Offer/Answer/ICE 중계)"]
        REST["REST API"]
    end

    Gemini["Google Gemini API"]
    TTS["TTS API"]
    DB[("MariaDB")]

    Chat -->|"텍스트 발행"| WS
    WS -->|"스트리밍 청크"| Chat
    WS --> Gemini
    Chat -.->|"실패 시 폴백"| REST

    Call -->|"Offer/Answer/ICE"| Signal
    Call <-->|"P2P 오디오"| Call
    Signal -.-> Gemini
    Signal -.-> TTS

    REST --> DB
    WS --> DB
```
