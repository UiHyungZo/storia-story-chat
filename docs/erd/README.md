# ERD

Week 1 기준 구현된 스키마 (`apps/backend/.../entity`), DB는 MariaDB. `User`는 `app_user`로, `Character`는 `story_character`로 매핑했습니다 — 둘 다 MariaDB/MySQL 예약어(`USER`, `CHARACTER`)와의 충돌을 피하기 위함입니다.

```mermaid
erDiagram
    APP_USER ||--o{ CONVERSATION : has
    STORY_CHARACTER ||--o{ CONVERSATION : has
    CONVERSATION ||--o{ MESSAGE : contains

    APP_USER {
        bigint id PK
        string deviceId UK
        timestamp createdAt
        timestamp lastActiveAt
    }

    STORY_CHARACTER {
        bigint id PK
        string name
        string concept
        text systemPrompt
        string ttsVoiceId
        timestamp createdAt
    }

    CONVERSATION {
        bigint id PK
        bigint user_id FK
        bigint character_id FK
        timestamp createdAt
    }

    MESSAGE {
        bigint id PK
        bigint conversation_id FK
        string role
        text content
        timestamp createdAt
    }
```

**제약조건**
- `CONVERSATION(user_id, character_id)` UNIQUE — 캐릭터당 하나의 연속 대화만 허용
- `MESSAGE(conversation_id)` 인덱스 — 히스토리 조회 최적화
- `APP_USER(deviceId)` UNIQUE — 디바이스 ID 기반 익명 세션 식별
