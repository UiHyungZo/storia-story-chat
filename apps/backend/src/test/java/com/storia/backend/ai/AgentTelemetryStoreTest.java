package com.storia.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.dto.AdminObservabilityResponse.Summary;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTelemetryStoreTest {

    private static AgentRunRecord record(String id, Instant at, long durationMs, String stop, String error,
                                         List<Long> latencies, Long tokens, List<AgentRunRecord.ToolRecord> tools) {
        return new AgentRunRecord(id, at, durationMs, stop, error, latencies.size(), latencies, null, null, tokens, tools);
    }

    @Test
    void keepsOnlyMostRecentRecords_newestFirst() {
        AgentTelemetryStore store = new AgentTelemetryStore();
        for (int i = 0; i < AgentTelemetryStore.MAX_RECORDS + 25; i++) {
            store.add(record("r" + i, Instant.now(), 10, "completed", null, List.of(5L), null, List.of()));
        }
        assertThat(store.summary().runs()).isEqualTo(AgentTelemetryStore.MAX_RECORDS);
        assertThat(store.recent(1).get(0).runId()).isEqualTo("r" + (AgentTelemetryStore.MAX_RECORDS + 24));
    }

    @Test
    void recordsOlderThanRetentionAreDropped() {
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        AgentTelemetryStore store = new AgentTelemetryStore(Clock.fixed(now, ZoneOffset.UTC));
        store.add(record("old", now.minus(AgentTelemetryStore.RETENTION).minusSeconds(1), 1, "completed", null, List.of(), null, List.of()));
        store.add(record("new", now.minusSeconds(60), 1, "completed", null, List.of(), null, List.of()));

        assertThat(store.recent(10)).extracting(AgentRunRecord::runId).containsExactly("new");
    }

    @Test
    void summary_aggregatesLatencyErrorsToolsAndTokens() {
        AgentTelemetryStore store = new AgentTelemetryStore();
        Instant now = Instant.now();
        store.add(record("a", now, 100, "completed", null, List.of(40L, 20L), 300L,
                List.of(new AgentRunRecord.ToolRecord("getCharacterConfig", true, null, 4))));
        store.add(record("b", now, 300, "error", "upstream_error", List.of(60L), null,
                List.of(new AgentRunRecord.ToolRecord("getCharacterConfig", false, "not_found", 8))));
        store.add(record("c", now, 200, "max_steps", null, List.of(), 100L, List.of()));

        Summary s = store.summary();

        assertThat(s.runs()).isEqualTo(3);
        assertThat(s.byStopReason()).containsEntry("completed", 1L).containsEntry("error", 1L).containsEntry("max_steps", 1L);
        assertThat(s.byErrorType()).containsExactly(java.util.Map.entry("upstream_error", 1L));
        assertThat(s.avgDurationMs()).isEqualTo(200.0);
        assertThat(s.p95DurationMs()).isEqualTo(300L);
        assertThat(s.avgModelLatencyMs()).isEqualTo(40.0);
        assertThat(s.totalTokens()).isEqualTo(400L);
        assertThat(s.tools()).hasSize(1);
        assertThat(s.tools().get(0).calls()).isEqualTo(2);
        assertThat(s.tools().get(0).errors()).isEqualTo(1);
        assertThat(s.tools().get(0).avgDurationMs()).isEqualTo(6.0);
    }

    @Test
    void emptyStore_reportsNullsInsteadOfZeros() {
        Summary s = new AgentTelemetryStore().summary();
        assertThat(s.runs()).isZero();
        assertThat(s.avgDurationMs()).isNull();
        assertThat(s.p95DurationMs()).isNull();
        assertThat(s.totalTokens()).isNull();
        assertThat(s.oldestRunAt()).isNull();
    }
}
