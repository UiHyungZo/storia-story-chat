package com.storia.backend.voice;

import java.io.ByteArrayOutputStream;
import lombok.Getter;

/** One turn of a voice call: accumulates egressed audio, then holds the processing result. */
public class VoiceTurnSession {

    public enum Status {
        RECORDING, PROCESSING, DONE, ERROR
    }

    @Getter
    private final String turnId;
    @Getter
    private final String deviceId;
    @Getter
    private final Long characterId;
    private final ByteArrayOutputStream audio = new ByteArrayOutputStream();

    @Getter
    private volatile Status status = Status.RECORDING;
    @Getter
    private volatile Long assistantMessageId;
    @Getter
    private volatile String errorMessage;

    public VoiceTurnSession(String turnId, String deviceId, Long characterId) {
        this.turnId = turnId;
        this.deviceId = deviceId;
        this.characterId = characterId;
    }

    public synchronized void appendAudio(byte[] chunk) {
        audio.write(chunk, 0, chunk.length);
    }

    public synchronized byte[] audioBytes() {
        return audio.toByteArray();
    }

    public void markProcessing() {
        status = Status.PROCESSING;
    }

    public void complete(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
        this.status = Status.DONE;
    }

    public void fail(String errorMessage) {
        this.errorMessage = errorMessage;
        this.status = Status.ERROR;
    }
}
