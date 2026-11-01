package com.storia.backend.eval;

import java.util.List;
import java.util.function.Function;

/** 결정적 테스트용 생성기. 외부 호출 없음. */
class FakeReplyGenerator implements ReplyGenerator {

    private final Function<String, String> byInput;

    FakeReplyGenerator(Function<String, String> byInput) {
        this.byInput = byInput;
    }

    @Override
    public String generate(String systemPrompt, List<ChatTurn> history, String input) {
        return byInput.apply(input);
    }

    @Override
    public String modelName() {
        return "fake";
    }

    @Override
    public String generationSettings() {
        return "deterministic fake (no model call)";
    }
}
