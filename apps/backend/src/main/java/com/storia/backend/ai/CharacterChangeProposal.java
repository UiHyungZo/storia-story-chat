package com.storia.backend.ai;

import com.storia.backend.entity.Character;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI가 만든 캐릭터 설정 변경 제안. DB 엔티티가 아니라 {@link ProposalStore}의 메모리에만 존재하며,
 * 승인 전에는 어떤 저장소도 변경하지 않는다. {@code proposedFields}는 제안된 필드만 담고(키: concept,
 * systemPrompt, ttsVoiceId), ttsVoiceId의 null 값은 "보이스 해제"를 뜻한다.
 */
public final class CharacterChangeProposal {

    private final String id;
    private final Long characterId;
    private final Map<String, String> proposedFields;
    private final String reason;
    private final String baseFingerprint;
    private final Instant createdAt;
    private ProposalStatus status = ProposalStatus.PENDING;
    private Instant decidedAt;

    CharacterChangeProposal(String id, Long characterId, Map<String, String> proposedFields, String reason,
                            String baseFingerprint, Instant createdAt) {
        this.id = id;
        this.characterId = characterId;
        this.proposedFields = Collections.unmodifiableMap(new LinkedHashMap<>(proposedFields));
        this.reason = reason;
        this.baseFingerprint = baseFingerprint;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public Long getCharacterId() { return characterId; }
    public Map<String, String> getProposedFields() { return proposedFields; }
    public String getReason() { return reason; }
    public String getBaseFingerprint() { return baseFingerprint; }
    public Instant getCreatedAt() { return createdAt; }
    public synchronized ProposalStatus getStatus() { return status; }
    public synchronized Instant getDecidedAt() { return decidedAt; }

    synchronized void setStatus(ProposalStatus newStatus) {
        this.status = newStatus;
        this.decidedAt = Instant.now();
    }

    /** 제안 시점의 설정(concept, systemPrompt, ttsVoiceId)을 식별하는 SHA-256. 승인 시 현재 값과 비교한다. */
    public static String fingerprint(Character character) {
        String joined = nullSafe(character.getConcept()) + '\u0000'
                + nullSafe(character.getSystemPrompt()) + '\u0000'
                + (character.getTtsVoiceId() == null ? "\u0001null" : character.getTtsVoiceId());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
