package com.storia.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.storia.backend.dto.AdminProposalResponse;
import com.storia.backend.entity.Character;
import com.storia.backend.exception.ConflictException;
import com.storia.backend.exception.ResourceNotFoundException;
import com.storia.backend.service.CharacterService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProposalApprovalServiceTest {

    @Mock private CharacterService characterService;

    private final ProposalStore store = new ProposalStore();
    private ProposalApprovalService service;
    private Character character;

    @BeforeEach
    void setUp() {
        service = new ProposalApprovalService(store, characterService);
        character = new Character();
        character.setId(1L);
        character.setName("루나");
        character.setConcept("원래 컨셉");
        character.setSystemPrompt("원래 프롬프트");
        character.setTtsVoiceId("ko-KR-Standard-A");
    }

    private CharacterChangeProposal propose(Map<String, String> fields) {
        return store.add(1L, fields, "이유", CharacterChangeProposal.fingerprint(character)).orElseThrow();
    }

    private Map<String, String> fields(String concept, String prompt) {
        Map<String, String> m = new LinkedHashMap<>();
        if (concept != null) m.put("concept", concept);
        if (prompt != null) m.put("systemPrompt", prompt);
        return m;
    }

    @Test
    void creatingAndViewingAProposal_neverCallsUpdate() {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));

        AdminProposalResponse view = service.get(p.getId());
        service.list();

        assertThat(view.status()).isEqualTo("PENDING");
        assertThat(view.current().concept()).isEqualTo("원래 컨셉");
        assertThat(view.proposed().concept()).isEqualTo("새 컨셉");
        assertThat(view.changedFields()).containsExactly("concept");
        assertThat(view.stale()).isFalse();
        verify(characterService, never()).update(any(), any(), any(), any());
    }

    @Test
    void approve_mergesOnlyProposedFieldsAndUpdatesExactlyOnce() {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));

        AdminProposalResponse result = service.approve(p.getId());

        verify(characterService, times(1)).update(1L, "새 컨셉", "원래 프롬프트", "ko-KR-Standard-A");
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(p.getDecidedAt()).isNotNull();
    }

    @Test
    void approve_explicitNullTtsVoice_clearsVoice() {
        when(characterService.findById(1L)).thenReturn(character);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("ttsVoiceId", null);
        CharacterChangeProposal p = propose(f);

        service.approve(p.getId());

        verify(characterService).update(1L, "원래 컨셉", "원래 프롬프트", null);
    }

    @Test
    void approveTwice_secondIsConflictAndUpdateRunsOnce() {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));
        service.approve(p.getId());

        assertThatThrownBy(() -> service.approve(p.getId())).isInstanceOf(ConflictException.class);
        verify(characterService, times(1)).update(any(), any(), any(), any());
    }

    @Test
    void reject_changesStatusOnly_andBlocksLaterApproval() {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));

        assertThat(service.reject(p.getId()).status()).isEqualTo("REJECTED");

        assertThatThrownBy(() -> service.approve(p.getId())).isInstanceOf(ConflictException.class);
        verify(characterService, never()).update(any(), any(), any(), any());
    }

    @Test
    void approve_staleProposal_isRefusedAndMarkedStale() {
        CharacterChangeProposal p = propose(fields("새 컨셉", null));
        // 제안 이후 다른 경로로 설정이 바뀜
        character.setSystemPrompt("누군가 수정한 프롬프트");
        when(characterService.findById(1L)).thenReturn(character);

        assertThatThrownBy(() -> service.approve(p.getId())).isInstanceOf(ConflictException.class);

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.STALE);
        verify(characterService, never()).update(any(), any(), any(), any());
        assertThat(service.get(p.getId()).stale()).isTrue();
        assertThatThrownBy(() -> service.approve(p.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void view_flagsPendingProposalAsStaleWhenCharacterChanged() {
        CharacterChangeProposal p = propose(fields("새 컨셉", null));
        character.setConcept("바뀐 컨셉");
        when(characterService.findById(1L)).thenReturn(character);

        assertThat(service.get(p.getId()).stale()).isTrue();
        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }

    @Test
    void approve_whenUpdateFails_proposalStaysPending() {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));
        when(characterService.update(any(), any(), any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.approve(p.getId())).isInstanceOf(RuntimeException.class);

        assertThat(p.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }

    @Test
    void approve_unknownProposal_isNotFound() {
        assertThatThrownBy(() -> service.approve("no-such-id")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.reject("no-such-id")).isInstanceOf(ResourceNotFoundException.class);
        verify(characterService, never()).update(any(), any(), any(), any());
    }

    @Test
    void approve_concurrentRequests_applyOnlyOnce() throws Exception {
        when(characterService.findById(1L)).thenReturn(character);
        CharacterChangeProposal p = propose(fields("새 컨셉", null));
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        var successes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                try {
                    service.approve(p.getId());
                    successes.incrementAndGet();
                } catch (ConflictException ignored) {
                }
            }));
        }
        for (var f : futures) f.get();
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        verify(characterService, times(1)).update(any(), any(), any(), any());
    }

    @Test
    void expiredProposals_areNotFound() {
        // TTL 지난 제안은 접근 시점에 정리된다 — 생성자를 통해 과거 시각으로 직접 주입
        ProposalStore expiring = new ProposalStore();
        var old = new CharacterChangeProposal("old-id", 1L, fields("x", null), "r", "fp",
                java.time.Instant.now().minus(ProposalStore.TTL).minusSeconds(5));
        try {
            var field = ProposalStore.class.getDeclaredField("proposals");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (Map<String, CharacterChangeProposal>) field.get(expiring);
            map.put(old.getId(), old);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        assertThat(expiring.find("old-id")).isEmpty();
    }
}
