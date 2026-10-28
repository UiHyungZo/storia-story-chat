package com.storia.backend.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 임시 디렉터리에 합성 문서를 만들고 검색 스택을 조립하는 테스트 헬퍼. */
final class RagTestSupport {

    static final List<KnowledgeSource> SOURCES = List.of(
            new KnowledgeSource("voice", "docs/voice.md", "음성 문서"),
            new KnowledgeSource("db", "docs/db.md", "DB 문서"),
            new KnowledgeSource("deploy", "docs/deploy.md", "배포 문서"));

    static final String VOICE = "# 음성 통화\n\n## 구조\n음성 통화는 LiveKit으로 연결되고 서버가 음성 인식 후 응답합니다. 통화 중 음성은 실시간으로 처리됩니다.\n";
    static final String DB = "# 데이터베이스\n\n## 선택\nMariaDB를 데이터베이스로 사용합니다. 데이터베이스 포트는 3307입니다 그리고 MariaDB 설정은 compose에 있습니다.\n";
    static final String DEPLOY = "# 배포\n\n## TestFlight\nTestFlight 배포는 fastlane으로 진행합니다. 배포 전에 인증서를 준비해야 하고 배포 절차가 길어요.\n";

    private RagTestSupport() {
    }

    static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    static void writeDefaults(Path root) throws IOException {
        write(root, "docs/voice.md", VOICE);
        write(root, "docs/db.md", DB);
        write(root, "docs/deploy.md", DEPLOY);
    }

    static KnowledgeSearchService service(Path root, EmbeddingProvider provider, RagMetrics metrics) {
        KnowledgeBase base = new KnowledgeBase(new DocumentLoader(root, SOURCES), provider, new MarkdownChunker(800), null);
        return new KnowledgeSearchService(base, metrics);
    }
}
