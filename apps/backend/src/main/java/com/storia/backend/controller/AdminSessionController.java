package com.storia.backend.controller;

import com.storia.backend.dto.AdminVoiceTurnResponse;
import com.storia.backend.voice.VoiceTurnRegistry;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 진행 중인 음성 통화 turn 상태를 읽기 전용으로 노출한다. VoiceTurnRegistry가 5분 stale sweep으로
 * 자연히 소수만 유지하므로 페이지네이션은 두지 않는다.
 */
@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
public class AdminSessionController {

    private final VoiceTurnRegistry voiceTurnRegistry;

    @GetMapping
    public List<AdminVoiceTurnResponse> list() {
        return voiceTurnRegistry.all().stream()
                .map(AdminVoiceTurnResponse::from)
                .sorted(Comparator.comparing(AdminVoiceTurnResponse::createdAt).reversed())
                .toList();
    }
}
