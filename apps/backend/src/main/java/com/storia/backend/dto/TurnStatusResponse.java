package com.storia.backend.dto;

/** status: "recording" | "processing" | "done" | "error" */
public record TurnStatusResponse(String status, Long assistantMessageId, String errorMessage) {
}
