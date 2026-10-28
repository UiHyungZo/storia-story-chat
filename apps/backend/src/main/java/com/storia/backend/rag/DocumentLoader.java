package com.storia.backend.rag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 허용 목록({@link KnowledgeSource})에 있는 Markdown 파일만 읽는다. 루트 밖으로 나가는 경로(.., 심볼릭 링크),
 * .md가 아닌 파일, 지나치게 큰 파일은 거부한다. 읽지 못한 소스는 예외 대신 missing 목록으로 보고한다.
 */
public final class DocumentLoader implements KnowledgeDocumentLoader {

    static final long MAX_FILE_BYTES = 1_000_000;

    public record Loaded(List<KnowledgeDocument> documents, List<String> missing, List<String> rejected) {
    }

    private final Path root;
    private final List<KnowledgeSource> sources;

    public DocumentLoader(Path root, List<KnowledgeSource> sources) {
        this.root = root.toAbsolutePath().normalize();
        this.sources = List.copyOf(sources);
    }

    public Path root() {
        return root;
    }

    public List<KnowledgeSource> sources() {
        return sources;
    }

    @Override
    public Loaded load() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            sources.forEach(s -> missing.add(s.path()));
            return new Loaded(documents, missing, rejected);
        }

        for (KnowledgeSource source : sources) {
            Path resolved = realRoot.resolve(source.path()).normalize();
            try {
                if (!source.path().toLowerCase(Locale.ROOT).endsWith(".md") || !resolved.startsWith(realRoot)) {
                    rejected.add(source.path());
                    continue;
                }
                if (!Files.isRegularFile(resolved)) {
                    missing.add(source.path());
                    continue;
                }
                Path real = resolved.toRealPath();
                if (!real.startsWith(realRoot) || Files.size(real) > MAX_FILE_BYTES) {
                    rejected.add(source.path());
                    continue;
                }
                TextSanitizer.Result cleaned = TextSanitizer.clean(Files.readString(real, StandardCharsets.UTF_8));
                documents.add(new KnowledgeDocument(source.id(), source.path(), source.title(), cleaned.text(),
                        cleaned.redactions()));
            } catch (IOException | RuntimeException e) {
                rejected.add(source.path());
            }
        }
        return new Loaded(documents, missing, rejected);
    }

    /** 변경 감지용 값 (경로, 크기, 수정 시각). 내용을 읽지 않아 저렴하다. */
    @Override
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (KnowledgeSource source : sources) {
            Path p = root.resolve(source.path()).normalize();
            try {
                sb.append(source.path()).append(':').append(Files.size(p)).append(':')
                        .append(Files.getLastModifiedTime(p).toMillis()).append(';');
            } catch (IOException e) {
                sb.append(source.path()).append(":missing;");
            }
        }
        return sb.toString();
    }
}
