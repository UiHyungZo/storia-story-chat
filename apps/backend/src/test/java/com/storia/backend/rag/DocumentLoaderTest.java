package com.storia.backend.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentLoaderTest {

    @Test
    void loadsOnlyAllowlistedMarkdown_andReportsMissing(@TempDir Path root) throws IOException {
        RagTestSupport.write(root, "docs/voice.md", RagTestSupport.VOICE);
        RagTestSupport.write(root, "docs/not-listed.md", "# 목록에 없음\n이 파일은 읽히면 안 됩니다.");

        DocumentLoader.Loaded loaded = new DocumentLoader(root, RagTestSupport.SOURCES).load();

        assertThat(loaded.documents()).extracting(KnowledgeDocument::id).containsExactly("voice");
        assertThat(loaded.missing()).containsExactly("docs/db.md", "docs/deploy.md");
        assertThat(loaded.documents().get(0).source()).isEqualTo("docs/voice.md");
    }

    @Test
    void rejectsPathTraversalNonMarkdownAndOversizedFiles(@TempDir Path root) throws IOException {
        Path outside = Files.createTempFile("outside", ".md");
        Files.writeString(outside, "# 바깥 파일\n루트 밖의 내용");
        RagTestSupport.write(root, "docs/big.md", "x".repeat((int) DocumentLoader.MAX_FILE_BYTES + 10));
        RagTestSupport.write(root, "docs/secret.txt", "비밀");
        List<KnowledgeSource> sources = List.of(
                new KnowledgeSource("trav", "../" + outside.getFileName(), "t"),
                new KnowledgeSource("abs", outside.toString(), "a"),
                new KnowledgeSource("big", "docs/big.md", "b"),
                new KnowledgeSource("txt", "docs/secret.txt", "s"));

        DocumentLoader.Loaded loaded = new DocumentLoader(root, sources).load();

        assertThat(loaded.documents()).isEmpty();
        assertThat(loaded.rejected()).hasSize(4);
        Files.deleteIfExists(outside);
    }

    @Test
    void rejectsSymlinkEscapingRoot(@TempDir Path root, @TempDir Path other) throws IOException {
        Path target = other.resolve("real.md");
        Files.writeString(target, "# 외부\n바깥 문서");
        Files.createDirectories(root.resolve("docs"));
        Files.createSymbolicLink(root.resolve("docs/link.md"), target);

        DocumentLoader.Loaded loaded = new DocumentLoader(root, List.of(new KnowledgeSource("link", "docs/link.md", "l"))).load();

        assertThat(loaded.documents()).isEmpty();
        assertThat(loaded.rejected()).containsExactly("docs/link.md");
    }

    @Test
    void secretsInDocumentsAreRedactedBeforeIndexing(@TempDir Path root) throws IOException {
        RagTestSupport.write(root, "docs/voice.md", RagTestSupport.VOICE + "\nGEMINI_API_KEY=AIzaSyA1234567890abcdefghijklmnopqrstuvw\n");

        KnowledgeDocument doc = new DocumentLoader(root, RagTestSupport.SOURCES).load().documents().get(0);

        assertThat(doc.text()).doesNotContain("AIzaSy");
        assertThat(doc.redactions()).isEqualTo(1);
    }

    @Test
    void fingerprintChangesWhenFileChanges(@TempDir Path root) throws IOException {
        RagTestSupport.writeDefaults(root);
        DocumentLoader loader = new DocumentLoader(root, RagTestSupport.SOURCES);
        String before = loader.fingerprint();
        RagTestSupport.write(root, "docs/voice.md", RagTestSupport.VOICE + "\n추가 내용으로 크기가 달라집니다.");
        assertThat(loader.fingerprint()).isNotEqualTo(before);
    }

    @Test
    void defaultAllowlistExcludesWorkLogsAndAgentInstructions() {
        assertThat(KnowledgeSources.DEFAULT).extracting(KnowledgeSource::path)
                .doesNotContain("HANDOFF.md", "TODO.md", "AGENTS.md", "CLAUDE.md")
                .allSatisfy(p -> assertThat(p).endsWith(".md"));
    }
}
