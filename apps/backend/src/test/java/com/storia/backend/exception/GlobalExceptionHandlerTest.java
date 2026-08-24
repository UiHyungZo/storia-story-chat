package com.storia.backend.exception;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.storia.backend.controller.ConversationController;
import com.storia.backend.service.ConversationService;
import com.storia.backend.service.GeminiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link GlobalExceptionHandler}가 실제 컨트롤러 경로에서 올바른 상태코드/바디로 매핑하는지 검증.
 * {@code ConversationController}를 대표 컨트롤러로 사용 — 404(리소스 없음), 400(검증 실패,
 * 헤더 누락) 세 가지 케이스를 모두 이 컨트롤러 하나로 재현할 수 있다.
 */
@WebMvcTest(ConversationController.class)
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ConversationService conversationService;
    @MockitoBean private GeminiService geminiService;

    @Test
    void resourceNotFound_mapsTo404() throws Exception {
        when(conversationService.getMessages("device-1", 999L))
                .thenThrow(new ResourceNotFoundException("Character not found: 999"));

        mockMvc.perform(get("/api/conversations/999/messages").header("X-Device-Id", "device-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found: 999"));
    }

    @Test
    void blankContent_mapsTo400ValidationError() throws Exception {
        mockMvc.perform(post("/api/conversations/1/messages")
                        .header("X-Device-Id", "device-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void missingDeviceIdHeader_mapsTo400MissingHeader() throws Exception {
        mockMvc.perform(get("/api/conversations/1/messages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.message").value("X-Device-Id 헤더가 필요합니다."));
    }
}
