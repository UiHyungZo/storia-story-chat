package com.storia.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.dto.MessageRequest;
import com.storia.backend.dto.StreamEvent;
import com.storia.backend.entity.Message;
import com.storia.backend.lore.CharacterLoreMetrics;
import com.storia.backend.lore.LoreTestSupport;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.service.ChatContextAssembler;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

/**
 * 실제 채팅 컨트롤러(REST, STOMP)에 캐릭터 세계관 RAG가 연결된 회귀 테스트. Gemini는 Mock이며 외부 호출은 없다.
 * 확인: 기능 꺼짐 = 기존과 동일한 프롬프트, 켜짐 = 세계관이 System Prompt에만 추가, 저장되는 메시지는 불변,
 * 검색 실패 = 기존 프롬프트로 응답, 스트리밍 이벤트 형식 유지.
 */
class ConversationLoreChatTest {

    private static final String BASE = "당신은 우주선 항법 AI 아리아입니다.";
    private static final String QUESTION = "초광속 도약은 하루에 몇 번 할 수 있어?";

    private ConversationService conversationService;
    private GeminiService geminiService;
    private Message userMessage;
    private Message assistantMessage;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        geminiService = mock(GeminiService.class);
        userMessage = new Message(null, Message.Role.USER, QUESTION);
        userMessage.setId(10L);
        assistantMessage = new Message(null, Message.Role.ASSISTANT, "하루에 한 번이에요.");
        assistantMessage.setId(11L);
        when(conversationService.postMessage("dev-1", 1L, QUESTION)).thenReturn(userMessage);
        when(conversationService.getMessages("dev-1", 1L)).thenReturn(List.of(userMessage));
        when(conversationService.getSystemPrompt(1L)).thenReturn(BASE);
        when(conversationService.postAssistantMessage(eq("dev-1"), eq(1L), any())).thenReturn(assistantMessage);
        when(geminiService.streamReply(any(), any())).thenReturn(Flux.just("하루에 ", "한 번이에요."));
    }

    private ChatContextAssembler assembler(boolean enabled) {
        return new ChatContextAssembler(LoreTestSupport.service(enabled, LocalHashingEmbeddingProvider::new, 0.08, 800,
                new CharacterLoreMetrics()), 3);
    }

    private MockMvc rest(boolean enabled) {
        return MockMvcBuilders.standaloneSetup(new ConversationController(conversationService, geminiService, assembler(enabled)))
                .build();
    }

    private String capturedPrompt() {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiService).streamReply(prompt.capture(), any());
        return prompt.getValue();
    }

    private void postRest(MockMvc mvc) throws Exception {
        mvc.perform(post("/api/conversations/1/messages").header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + QUESTION + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assistantMessage.content").value("하루에 한 번이에요."))
                .andExpect(jsonPath("$.userMessage.content").value(QUESTION));
    }

    @Test
    void rest_flagOff_sendsExactlyTheBasePrompt() throws Exception {
        postRest(rest(false));
        assertThat(capturedPrompt()).isEqualTo(BASE);
    }

    @Test
    void rest_flagOn_addsLoreToSystemPromptOnly_andStoresNothingExtra() throws Exception {
        postRest(rest(true));
        String prompt = capturedPrompt();
        assertThat(prompt).startsWith(BASE).contains("하루에 한 번").contains("<lore-reference>");

        // 저장되는 것은 사용자 원문과 모델 응답뿐이다 (검색 결과는 저장하지 않는다).
        verify(conversationService).postMessage("dev-1", 1L, QUESTION);
        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(conversationService).postAssistantMessage(eq("dev-1"), eq(1L), saved.capture());
        assertThat(saved.getValue()).isEqualTo("하루에 ".concat("한 번이에요.")).doesNotContain("lore-reference");
        ArgumentCaptor<List<Message>> history = ArgumentCaptor.forClass(List.class);
        verify(geminiService).streamReply(any(), history.capture());
        assertThat(history.getValue()).containsExactly(userMessage);
    }

    @Test
    void rest_flagOn_unknownCharacterNameFallsBackToBasePrompt() throws Exception {
        // ID 99는 이름 매핑이 없다 → 세계관 없이 기존 프롬프트.
        when(conversationService.postMessage("dev-1", 99L, QUESTION)).thenReturn(userMessage);
        when(conversationService.getMessages("dev-1", 99L)).thenReturn(List.of(userMessage));
        when(conversationService.getSystemPrompt(99L)).thenReturn("다른 프롬프트");
        when(conversationService.postAssistantMessage(eq("dev-1"), eq(99L), any())).thenReturn(assistantMessage);
        rest(true).perform(post("/api/conversations/99/messages").header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + QUESTION + "\"}"))
                .andExpect(status().isCreated());
        assertThat(capturedPrompt()).isEqualTo("다른 프롬프트");
    }

    @Test
    void rest_geminiFailureStillReturnsTheExistingFallbackMessage() throws Exception {
        when(geminiService.streamReply(any(), any())).thenReturn(Flux.error(new IllegalStateException("down")));
        rest(true).perform(post("/api/conversations/1/messages").header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"" + QUESTION + "\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(conversationService).postAssistantMessage(eq("dev-1"), eq(1L), saved.capture());
        assertThat(saved.getValue()).isEqualTo("죄송해요, 지금은 답변을 생성할 수 없어요.");
    }

    private List<StreamEvent> stomp(boolean enabled) {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        ConversationStompController controller =
                new ConversationStompController(conversationService, geminiService, assembler(enabled), template);
        controller.handleMessage(1L, "dev-1", new MessageRequest(QUESTION));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, timeout(3_000).times(3)).convertAndSend(eq("/topic/conversation/dev-1/1"), payload.capture());
        List<StreamEvent> events = new ArrayList<>();
        payload.getAllValues().forEach(p -> events.add((StreamEvent) p));
        return events;
    }

    @Test
    void stomp_flagOn_keepsStreamEventFormatAndAddsLoreToPromptOnly() {
        List<StreamEvent> events = stomp(true);
        assertThat(events).extracting(StreamEvent::type).containsExactly("CHUNK", "CHUNK", "DONE");
        assertThat(events.get(0).content()).isEqualTo("하루에 ");
        assertThat(events.get(2).messageId()).isEqualTo(11L);
        assertThat(capturedPrompt()).startsWith(BASE).contains("하루에 한 번").contains("<lore-reference>");
        verify(conversationService).postMessage("dev-1", 1L, QUESTION);
        verify(conversationService).postAssistantMessage("dev-1", 1L, "하루에 한 번이에요.");
    }

    @Test
    void stomp_flagOff_isIdenticalToTheLegacyBehavior() {
        List<StreamEvent> events = stomp(false);
        assertThat(events).extracting(StreamEvent::type).containsExactly("CHUNK", "CHUNK", "DONE");
        assertThat(capturedPrompt()).isEqualTo(BASE);
    }

    @Test
    void stomp_errorEventIsUnchangedWhenGeminiFails() {
        when(geminiService.streamReply(any(), any())).thenReturn(Flux.error(new IllegalStateException("down")));
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        new ConversationStompController(conversationService, geminiService, assembler(true), template)
                .handleMessage(1L, "dev-1", new MessageRequest(QUESTION));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, timeout(3_000)).convertAndSend(eq("/topic/conversation/dev-1/1"), payload.capture());
        StreamEvent event = (StreamEvent) payload.getValue();
        assertThat(event.type()).isEqualTo("ERROR");
        assertThat(event.content()).isEqualTo("죄송해요, 지금은 답변을 생성할 수 없어요.");
        verify(conversationService, never()).postAssistantMessage(any(), any(), any());
    }
}
