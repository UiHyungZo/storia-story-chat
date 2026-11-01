package com.storia.backend.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storia.backend.config.LiveKitProperties;
import com.storia.backend.entity.Message;
import com.storia.backend.lore.CharacterLoreMetrics;
import com.storia.backend.lore.LoreTestSupport;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.service.ChatContextAssembler;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import com.storia.backend.service.SttService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/** 축소판 음성 경로(VoiceCallService.completeTurn)의 응답 생성에 세계관이 적용되는지 확인한다. 외부 호출 없음. */
class VoiceCallServiceLoreTest {

    private static final String BASE = "당신은 우주선 항법 AI 아리아입니다.";

    private String runTurn(boolean enabled) {
        SttService stt = mock(SttService.class);
        GeminiService gemini = mock(GeminiService.class);
        ConversationService conversations = mock(ConversationService.class);
        VoiceTurnRegistry registry = new VoiceTurnRegistry();
        Message user = new Message(null, Message.Role.USER, "초광속 도약은 하루에 몇 번 할 수 있어?");
        Message reply = new Message(null, Message.Role.ASSISTANT, "하루에 한 번이에요.");
        reply.setId(5L);

        VoiceTurnSession session = registry.create("dev-1", 1L);
        session.appendAudio(new byte[] {1, 2, 3});
        when(stt.transcribe(any())).thenReturn(user.getContent());
        when(conversations.getMessages("dev-1", 1L)).thenReturn(List.of(user));
        when(conversations.getSystemPrompt(1L)).thenReturn(BASE);
        when(conversations.postAssistantMessage("dev-1", 1L, "하루에 한 번이에요.")).thenReturn(reply);
        when(gemini.streamReply(any(), any())).thenReturn(Flux.just("하루에 한 번이에요."));

        ChatContextAssembler assembler = new ChatContextAssembler(LoreTestSupport.service(enabled,
                LocalHashingEmbeddingProvider::new, 0.08, 800, new CharacterLoreMetrics()), 3);
        VoiceCallService service = new VoiceCallService(mock(LiveKitProperties.class), registry, stt, gemini,
                conversations, assembler);

        service.completeTurn(session.getTurnId());

        assertThat(session.getStatus()).isEqualTo(VoiceTurnSession.Status.DONE);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).streamReply(prompt.capture(), any());
        return prompt.getValue();
    }

    @Test
    void flagOnAddsLoreToTheVoiceTurnPrompt() {
        assertThat(runTurn(true)).startsWith(BASE).contains("하루에 한 번").contains("<lore-reference>");
    }

    @Test
    void flagOffKeepsTheLegacyPrompt() {
        assertThat(runTurn(false)).isEqualTo(BASE);
    }
}
