package com.storia.backend.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.storia.backend.config.GeminiProperties;
import com.storia.backend.entity.Message;
import com.storia.backend.service.GeminiService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 실제 Gemini를 호출하는 평가용 생성기 (live 평가 전용). 운영 {@link GeminiService}를 그대로
 * 재사용하되 DB·대화 저장 경로는 거치지 않는다. {@link Message}는 conversation 없이 메모리에서만 생성한다.
 * 비용이 발생하므로 {@code LiveEvalTest}에서 명시적 환경변수로만 사용된다.
 */
class GeminiReplyGenerator implements ReplyGenerator {

    private static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(90);

    private final GeminiService geminiService;
    private final String model;

    GeminiReplyGenerator(String apiKey, String model, String baseUrl) {
        GeminiProperties properties = new GeminiProperties(apiKey, model, baseUrl);
        this.geminiService = new GeminiService(
                WebClient.builder().baseUrl(baseUrl).build(), properties, new ObjectMapper());
        this.model = model;
    }

    @Override
    public String generate(String systemPrompt, List<ChatTurn> history, String input) {
        List<Message> messages = new ArrayList<>();
        for (ChatTurn turn : history) {
            messages.add(new Message(null, turn.isUser() ? Message.Role.USER : Message.Role.ASSISTANT, turn.content()));
        }
        messages.add(new Message(null, Message.Role.USER, input));
        return geminiService.streamReply(systemPrompt, messages)
                .collect(Collectors.joining())
                .block(TOTAL_TIMEOUT);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String generationSettings() {
        return "provider defaults (GeminiService는 generationConfig를 전송하지 않음: temperature 등 미지정)";
    }
}
