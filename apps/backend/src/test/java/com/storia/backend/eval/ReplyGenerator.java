package com.storia.backend.eval;

import java.util.List;

/**
 * 평가용 응답 생성 포트. 운영 대화 저장 경로(ConversationService 등)를 거치지 않고
 * system prompt + 합성 히스토리 + 입력만으로 응답을 만든다.
 */
public interface ReplyGenerator {

    String generate(String systemPrompt, List<ChatTurn> history, String input) throws Exception;

    /** 리포트에 기록할 모델 식별자. */
    String modelName();

    /** 리포트에 기록할 생성 설정 설명. */
    String generationSettings();
}
