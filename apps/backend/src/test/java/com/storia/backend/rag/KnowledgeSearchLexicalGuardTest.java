package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.rag.KnowledgeSearchResult.Status;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 어휘 근거 검사: 점수 통과와 근거 채택을 구분한다. 기본은 꺼져 있고(캐릭터 RAG 등 기존 사용처), 의미 임베딩엔 적용하지 않는다. */
class KnowledgeSearchLexicalGuardTest {

    /** 모든 조각이 점수를 통과하도록 임계값을 끈 로컬 해싱 인덱스 — 점수만으로는 무관한 문서도 결과에 들어온다. */
    private static KnowledgeSearchService service(Path root, EmbeddingProvider provider, boolean guard) {
        KnowledgeBase base = new KnowledgeBase(new DocumentLoader(root, RagTestSupport.SOURCES), provider,
                new MarkdownChunker(800), -1.0);
        return new KnowledgeSearchService(base, new RagMetrics(), guard);
    }

    @Test
    void contentTerms_dropQuestionWordsAndKoreanEndings() {
        assertThat(LexicalSupport.contentTerms("Storia 결제 시스템은 어떻게 동작해?")).containsExactly("결제");
        assertThat(LexicalSupport.contentTerms("음성 통화는 어떤 구조로 동작해?")).containsExactly("음성", "통화");
        assertThat(LexicalSupport.contentTerms("TestFlight로 배포하려면")).containsExactly("testflight", "배포");
        assertThat(LexicalSupport.contentTerms("WebRTC를 LiveKit으로")).containsExactly("webrtc", "livekit");
        assertThat(LexicalSupport.contentTerms("payment API")).containsExactly("payment", "api");
        assertThat(LexicalSupport.contentTerms("어떻게 해?")).isEmpty();
    }

    @Test
    void guardOff_scorePassingUnrelatedChunksAreReturned_asBefore(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);

        KnowledgeSearchResult result = service(root, new LocalHashingEmbeddingProvider(), false).search("payment refund", 3);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.results()).isNotEmpty();
    }

    @Test
    void guardOn_chunksWithoutAnyQueryTermAreNotEvidence(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);

        KnowledgeSearchResult result = service(root, new LocalHashingEmbeddingProvider(), true).search("payment refund", 3);

        assertThat(result.status()).isEqualTo(Status.NO_EVIDENCE);
        assertThat(result.results()).isEmpty();
    }

    @Test
    void guardOn_keepsOnlyChunksContainingATerm_andRenumbersRanks(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);

        KnowledgeSearchResult result = service(root, new LocalHashingEmbeddingProvider(), true).search("배포 결제", 3);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.results()).extracting(KnowledgeSearchResult.Hit::docId).containsExactly("deploy");
        assertThat(result.results().get(0).rank()).isEqualTo(1);
    }

    @Test
    void guardOn_onlyQuestionWords_fallsBackToScore(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);

        KnowledgeSearchResult result = service(root, new LocalHashingEmbeddingProvider(), true).search("어떻게 해?", 3);

        assertThat(result.results()).hasSize(3);
    }

    @Test
    void guardOn_isNotAppliedToSemanticEmbeddings(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);
        EmbeddingProvider semantic = new LocalHashingEmbeddingProvider() {
            @Override
            public boolean semantic() {
                return true;
            }
        };

        KnowledgeSearchResult result = service(root, semantic, true).search("payment refund", 3);

        assertThat(result.status()).isEqualTo(Status.OK);
        assertThat(result.results()).hasSize(3);
    }

    @Test
    void sourceRoles_separateCurrentDocsFromHistoricalRecords() {
        assertThat(KnowledgeSources.roleOf("docs/architecture/README.md")).isEqualTo(KnowledgeSources.ROLE_CURRENT);
        assertThat(KnowledgeSources.roleOf("PRD/Storia_PRD_final.md")).isEqualTo(KnowledgeSources.ROLE_CURRENT);
        assertThat(KnowledgeSources.roleOf("docs/decisions.md")).isEqualTo(KnowledgeSources.ROLE_HISTORICAL);
        assertThat(KnowledgeSources.roleOf("docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md")).isEqualTo(KnowledgeSources.ROLE_HISTORICAL);
        // 역할 목록의 경로는 모두 허용 목록 안에 있어야 한다(오타로 조용히 current가 되는 것 방지).
        assertThat(KnowledgeSources.DEFAULT).extracting(KnowledgeSource::path)
                .contains("docs/decisions.md", "docs/blog-webrtc-tradeoffs.md",
                        "docs/roadmap/STORIA_ADMIN_AI_AGENT_ROADMAP.md", "docs/architecture/admin-ai-agent-phase0.md");
    }
}
