package com.storia.backend.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.storia.backend.entity.Character;
import com.storia.backend.entity.Conversation;
import com.storia.backend.entity.Message;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.service.AdminConversationService;
import com.storia.backend.service.CharacterService;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 관리자 AI Agent가 쓸 수 있는 Read-only 도구의 고정 허용 목록. 모델이 만든 이름·인자는 신뢰하지 않고
 * 여기서 모두 검증한다. 이 클래스는 조회 메서드(findById / search / getMessages)만 호출한다.
 */
@Component
@Slf4j
public class AdminToolRegistry {

    public static final String GET_CHARACTER_CONFIG = "getCharacterConfig";
    public static final String GET_CONVERSATION_LOGS = "getConversationLogs";

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int MAX_PAGE = 1000;
    static final int MAX_CONTENT_CHARS = 500;
    static final int MAX_PROMPT_CHARS = 4000;
    static final int MAX_DEVICE_ID_CHARS = 100;
    static final int MAX_RESULT_CHARS = 20_000;

    private static final Set<String> CHARACTER_ARGS = Set.of("characterId");
    private static final Set<String> LOG_ARGS =
            Set.of("characterId", "deviceId", "from", "to", "conversationId", "limit", "page");

    private final CharacterService characterService;
    private final AdminConversationService adminConversationService;
    private final ObjectMapper objectMapper;

    public AdminToolRegistry(CharacterService characterService,
                             AdminConversationService adminConversationService,
                             ObjectMapper objectMapper) {
        this.characterService = characterService;
        this.adminConversationService = adminConversationService;
        this.objectMapper = objectMapper;
    }

    /** Gemini {@code tools[].functionDeclarations} 배열. */
    public ArrayNode declarations() {
        ArrayNode list = objectMapper.createArrayNode();

        ObjectNode character = list.addObject();
        character.put("name", GET_CHARACTER_CONFIG);
        character.put("description", "캐릭터 한 명의 설정(이름, 컨셉, 시스템 프롬프트, TTS 보이스)을 조회한다. 읽기 전용.");
        ObjectNode characterProps = parameters(character);
        characterProps.putObject("characterId").put("type", "integer").put("description", "캐릭터 ID (양수)");
        ((ArrayNode) character.path("parameters").path("required")).add("characterId");

        ObjectNode logs = list.addObject();
        logs.put("name", GET_CONVERSATION_LOGS);
        logs.put("description", "대화 로그를 조회한다. conversationId 없이 호출하면 대화 목록(필터 가능), "
                + "conversationId를 주면 해당 대화의 메시지 페이지를 반환한다. 읽기 전용이며 최대 "
                + MAX_LIMIT + "건. deviceId는 마스킹되어 반환된다.");
        ObjectNode logProps = parameters(logs);
        logProps.putObject("characterId").put("type", "integer").put("description", "캐릭터 ID 필터 (목록 모드)");
        logProps.putObject("deviceId").put("type", "string").put("description", "기기 ID 전체 일치 필터 (목록 모드)");
        logProps.putObject("from").put("type", "string").put("description", "시작일 yyyy-MM-dd (목록 모드)");
        logProps.putObject("to").put("type", "string").put("description", "종료일 yyyy-MM-dd, 포함 (목록 모드)");
        logProps.putObject("conversationId").put("type", "integer").put("description", "지정 시 메시지 모드. 다른 필터와 함께 쓸 수 없음");
        logProps.putObject("limit").put("type", "integer").put("description", "반환 건수 1~" + MAX_LIMIT + " (기본 " + DEFAULT_LIMIT + ")");
        logProps.putObject("page").put("type", "integer").put("description", "0부터 시작하는 페이지 번호");
        return list;
    }

    private ObjectNode parameters(ObjectNode declaration) {
        ObjectNode parameters = declaration.putObject("parameters");
        parameters.put("type", "object");
        parameters.set("required", objectMapper.createArrayNode());
        return parameters.putObject("properties");
    }

    /** 절대 예외를 던지지 않는다. 알 수 없는 도구·잘못된 인자·없는 데이터는 ok=false 결과로 돌려준다. */
    public ToolOutcome execute(String name, JsonNode args) {
        try {
            JsonNode result = switch (name == null ? "" : name) {
                case GET_CHARACTER_CONFIG -> getCharacterConfig(args);
                case GET_CONVERSATION_LOGS -> getConversationLogs(args);
                default -> throw new ToolArgumentException("unknown_tool");
            };
            if (result.toString().length() > MAX_RESULT_CHARS) {
                return error("result_too_large", "결과가 너무 큽니다. limit을 줄이세요.");
            }
            return new ToolOutcome(true, result);
        } catch (ToolArgumentException e) {
            return "unknown_tool".equals(e.getMessage())
                    ? error("unknown_tool", "허용되지 않은 도구입니다.")
                    : error("invalid_arguments", e.getMessage());
        } catch (ResourceNotFoundException e) {
            return error("not_found", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Admin tool {} failed", name, e);
            return error("tool_error", "도구 실행 중 오류가 발생했습니다.");
        }
    }

    private JsonNode getCharacterConfig(JsonNode args) {
        ObjectNode in = object(args, CHARACTER_ARGS);
        long characterId = positiveLong(in, "characterId", true);
        Character character = characterService.findById(characterId);

        ObjectNode out = objectMapper.createObjectNode();
        out.put("id", character.getId());
        out.put("name", character.getName());
        out.put("concept", character.getConcept());
        putTruncated(out, "systemPrompt", character.getSystemPrompt(), MAX_PROMPT_CHARS);
        if (character.getTtsVoiceId() == null) {
            out.putNull("ttsVoiceId");
        } else {
            out.put("ttsVoiceId", character.getTtsVoiceId());
        }
        return out;
    }

    private JsonNode getConversationLogs(JsonNode args) {
        ObjectNode in = object(args, LOG_ARGS);
        int limit = limit(in);
        int page = intInRange(in, "page", 0, MAX_PAGE, 0);

        if (in.has("conversationId")) {
            for (String filter : Set.of("characterId", "deviceId", "from", "to")) {
                if (in.has(filter)) {
                    throw new ToolArgumentException("conversationId는 " + filter + " 필터와 함께 쓸 수 없습니다.");
                }
            }
            return messages(positiveLong(in, "conversationId", true), limit, page);
        }
        return conversations(in, limit, page);
    }

    private JsonNode conversations(ObjectNode in, int limit, int page) {
        Long characterId = in.has("characterId") ? positiveLong(in, "characterId", true) : null;
        String deviceId = in.has("deviceId") ? text(in, "deviceId", MAX_DEVICE_ID_CHARS) : null;
        LocalDate from = date(in, "from");
        LocalDate to = date(in, "to");
        if (from != null && to != null && from.isAfter(to)) {
            throw new ToolArgumentException("from은 to보다 늦을 수 없습니다.");
        }

        Page<Conversation> result = adminConversationService.search(characterId, deviceId, from, to,
                PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        ObjectNode out = objectMapper.createObjectNode();
        out.put("mode", "conversations");
        out.put("totalElements", result.getTotalElements());
        out.put("page", page);
        out.put("returned", result.getNumberOfElements());
        ArrayNode items = out.putArray("conversations");
        for (Conversation c : result.getContent()) {
            ObjectNode item = items.addObject();
            item.put("id", c.getId());
            item.put("deviceIdMasked", maskDeviceId(c.getUser().getDeviceId()));
            item.put("characterId", c.getCharacter().getId());
            item.put("characterName", c.getCharacter().getName());
            item.put("createdAt", c.getCreatedAt().toString());
        }
        return out;
    }

    private JsonNode messages(long conversationId, int limit, int page) {
        Page<Message> result = adminConversationService.getMessages(conversationId, PageRequest.of(page, limit));

        ObjectNode out = objectMapper.createObjectNode();
        out.put("mode", "messages");
        out.put("conversationId", conversationId);
        out.put("totalElements", result.getTotalElements());
        out.put("page", page);
        out.put("returned", result.getNumberOfElements());
        ArrayNode items = out.putArray("messages");
        for (Message m : result.getContent()) {
            ObjectNode item = items.addObject();
            item.put("id", m.getId());
            item.put("role", m.getRole().name());
            putTruncated(item, "content", m.getContent(), MAX_CONTENT_CHARS);
            item.put("createdAt", m.getCreatedAt().toString());
        }
        return out;
    }

    // ---- 인자 검증 ----

    private ObjectNode object(JsonNode args, Set<String> allowed) {
        if (args == null || args.isNull() || args.isMissingNode()) {
            return objectMapper.createObjectNode();
        }
        if (!args.isObject()) {
            throw new ToolArgumentException("인자는 객체여야 합니다.");
        }
        args.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new ToolArgumentException("허용되지 않은 인자: " + field);
            }
        });
        return (ObjectNode) args;
    }

    private long positiveLong(ObjectNode in, String field, boolean required) {
        JsonNode node = in.get(field);
        if (node == null || node.isNull()) {
            if (required) {
                throw new ToolArgumentException(field + "는 필수입니다.");
            }
            return 0;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
            throw new ToolArgumentException(field + "는 양의 정수여야 합니다.");
        }
        return node.asLong();
    }

    private int limit(ObjectNode in) {
        return intInRange(in, "limit", 1, MAX_LIMIT, DEFAULT_LIMIT);
    }

    private int intInRange(ObjectNode in, String field, int min, int max, int fallback) {
        JsonNode node = in.get(field);
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < min || node.asInt() > max) {
            throw new ToolArgumentException(field + "는 " + min + "~" + max + " 사이 정수여야 합니다.");
        }
        return node.asInt();
    }

    private String text(ObjectNode in, String field, int maxChars) {
        JsonNode node = in.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank() || node.asText().length() > maxChars) {
            throw new ToolArgumentException(field + "는 1~" + maxChars + "자 문자열이어야 합니다.");
        }
        return node.asText();
    }

    private LocalDate date(ObjectNode in, String field) {
        if (!in.has(field)) {
            return null;
        }
        try {
            return LocalDate.parse(text(in, field, 10));
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException(field + "는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    // ---- 출력 가공 ----

    static String maskDeviceId(String deviceId) {
        if (deviceId == null || deviceId.length() <= 4) {
            return "****";
        }
        return deviceId.substring(0, 4) + "****";
    }

    private void putTruncated(ObjectNode out, String field, String value, int maxChars) {
        String safe = value == null ? "" : value;
        int codePoints = safe.codePointCount(0, safe.length());
        if (codePoints <= maxChars) {
            out.put(field, safe);
            return;
        }
        out.put(field, safe.substring(0, safe.offsetByCodePoints(0, maxChars)));
        out.put(field + "Truncated", true);
    }

    private ToolOutcome error(String code, String message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("error", code);
        node.put("message", message);
        return new ToolOutcome(false, node);
    }
}
