package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownChunkerTest {

    private static KnowledgeDocument doc(String text) {
        return new KnowledgeDocument("d", "docs/d.md", "문서", text, 0);
    }

    @Test
    void splitsByHeadingWithHeadingPath() {
        String md = "# 제목\n소개 문단입니다. 충분히 긴 소개 문단입니다 여기까지 이어지는 문장입니다.\n\n## 설치\n설치 방법은 다음과 같습니다. 여러 단계를 거치며 순서대로 진행합니다.\n\n### 상세\n상세 설명 문단입니다. 이것은 더 깊은 하위 항목이며 충분히 깁니다.";
        List<KnowledgeChunk> chunks = new MarkdownChunker(800).chunk(doc(md));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(1).heading()).isEqualTo("제목 > 설치");
        assertThat(chunks.get(2).heading()).isEqualTo("제목 > 설치 > 상세");
        assertThat(chunks.get(2).embeddingText()).startsWith("문서 > 제목 > 설치 > 상세\n");
        assertThat(chunks).extracting(KnowledgeChunk::chunkId).containsExactly("d#0", "d#1", "d#2");
    }

    @Test
    void headingsInsideCodeFencesAreNotHeadings() {
        String md = "# 제목\n```bash\n# 이건 주석이지 헤딩이 아님\nls -la\n```\n뒤따르는 설명 문장이 충분히 길게 이어집니다 정말로요.";
        List<KnowledgeChunk> chunks = new MarkdownChunker(800).chunk(doc(md));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("# 이건 주석이지");
    }

    @Test
    void longTextIsSplitWithinMaxCharsAndNothingIsLost() {
        StringBuilder sb = new StringBuilder("# 긴 문서\n");
        for (int i = 0; i < 30; i++) {
            sb.append("문단 ").append(i).append("번의 내용입니다. ").append("가나다라마바사 ".repeat(15)).append("\n\n");
        }
        List<KnowledgeChunk> chunks = new MarkdownChunker(500).chunk(doc(sb.toString()));

        assertThat(chunks).hasSizeGreaterThan(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(500));
        String joined = String.join("\n", chunks.stream().map(KnowledgeChunk::text).toList());
        for (int i = 0; i < 30; i++) {
            assertThat(joined).contains("문단 " + i + "번의 내용입니다.");
        }
    }

    @Test
    void veryLongSingleLineIsHardSplit() {
        List<KnowledgeChunk> chunks = new MarkdownChunker(300).chunk(doc("# 제목\n" + "단어 ".repeat(400)));
        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(300));
    }

    @Test
    void tinyPieceMergesOnlyWithinTheSameHeading() {
        // 같은 헤딩 안의 짧은 꼬리 조각은 합쳐지고, 다른 헤딩의 짧은 섹션은 헤딩 정보를 지키려고 분리된다.
        StringBuilder md = new StringBuilder("# 제목\n");
        md.append("가".repeat(250)).append("\n\n").append("나".repeat(250)).append("\n\n짧은 꼬리\n\n## 끝\n짧음");
        List<KnowledgeChunk> chunks = new MarkdownChunker(300).chunk(doc(md.toString()));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(1).text()).endsWith("짧은 꼬리");
        assertThat(chunks.get(2).heading()).isEqualTo("제목 > 끝");
        assertThat(chunks.get(2).text()).isEqualTo("짧음");
    }

    @Test
    void contentHashDependsOnEmbeddingText_andEmptyDocYieldsNoChunks() {
        List<KnowledgeChunk> a = new MarkdownChunker(800).chunk(doc("# 제목\n본문 내용은 이렇게 충분히 긴 한 줄입니다 정말."));
        List<KnowledgeChunk> b = new MarkdownChunker(800).chunk(doc("# 제목\n본문 내용은 이렇게 충분히 긴 한 줄입니다 정말!"));
        assertThat(a.get(0).contentHash()).isNotEqualTo(b.get(0).contentHash()).hasSize(64);
        assertThat(new MarkdownChunker(800).chunk(doc(""))).isEmpty();
        assertThatThrownBy(() -> new MarkdownChunker(10)).isInstanceOf(IllegalArgumentException.class);
    }
}
