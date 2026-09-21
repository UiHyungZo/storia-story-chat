package com.storia.backend.controller;

import com.storia.backend.dto.AdminConversationSummaryResponse;
import com.storia.backend.dto.MessageResponse;
import com.storia.backend.service.AdminConversationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/conversations")
@RequiredArgsConstructor
public class AdminConversationController {

    private final AdminConversationService adminConversationService;

    @GetMapping
    public PagedModel<AdminConversationSummaryResponse> search(
            @RequestParam(required = false) Long characterId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Direction.DESC) Pageable pageable) {
        return new PagedModel<>(adminConversationService.search(characterId, deviceId, from, to, pageable)
                .map(AdminConversationSummaryResponse::from));
    }

    @GetMapping("/{id}/messages")
    public PagedModel<MessageResponse> getMessages(
            @PathVariable Long id,
            @PageableDefault(size = 50, sort = "createdAt", direction = Direction.ASC) Pageable pageable) {
        return new PagedModel<>(adminConversationService.getMessages(id, pageable).map(MessageResponse::from));
    }
}
