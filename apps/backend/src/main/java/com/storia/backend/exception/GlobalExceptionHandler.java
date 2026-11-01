package com.storia.backend.exception;

import com.storia.backend.dto.ErrorResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * REST 컨트롤러(`@RestController`) 전역 예외 처리기. STOMP 경로(`ConversationStompController`)와
 * WS raw 핸들러(`VoiceEgressWebSocketHandler`)에는 적용되지 않는다 — 그쪽은 이미 자체적으로
 * `StreamEvent.error(...)`/`VoiceTurnSession#fail(...)`로 클라이언트에 에러를 알린다(docs/api.md 참고).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("MISSING_HEADER", e.getHeaderName() + " 헤더가 필요합니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    // VoiceCallService#startTurn이 LiveKit Egress 호출 실패 시 던짐 — 우리 잘못이 아니라 외부
    // 의존성 실패이므로 500이 아니라 502로 구분한다.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamFailure(IllegalStateException e) {
        log.warn("Upstream dependency failure", e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("UPSTREAM_ERROR", "외부 서비스 호출에 실패했습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 응답을 쓰는 도중 클라이언트가 연결을 끊은 경우(Broken pipe/Connection reset). 오디오 플레이어가 첫 GET을
        // 중간에 끊거나 부하 도구가 시간 제한으로 연결을 닫을 때 관측됐다. 서버 장애가 아니고 응답도 더 쓸 수 없으므로
        // ERROR로 남기지 않고 바디 없이 끝낸다(null = 처리 완료). 그 밖의 예외는 그대로 500.
        if (isClientDisconnect(e)) {
            log.debug("Client disconnected before the response was written: {}", e.getMessage());
            return null;
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "예상치 못한 오류가 발생했습니다."));
    }

    /** Spring은 클라이언트 연결이 끊겨 응답을 쓸 수 없을 때 {@link AsyncRequestNotUsableException}을 던진다(직접 또는 원인으로). */
    static boolean isClientDisconnect(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof AsyncRequestNotUsableException) {
                return true;
            }
        }
        return false;
    }
}
