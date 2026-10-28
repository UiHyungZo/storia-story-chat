package com.storia.backend.rag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 클래스패스 리소스(JAR 안에 포함되는 Markdown)를 읽는다. 허용 목록({@link KnowledgeSource})에 있는 .md 경로만
 * 읽고 크기 제한을 적용한다. 배포 시 파일 시스템의 docs 디렉터리에 의존하지 않는다 (Docker 이미지는
 * apps/backend만 복사하므로 저장소 루트의 docs/는 컨테이너에 없다).
 */
public final class ClasspathDocumentLoader implements KnowledgeDocumentLoader {

    private final ClassLoader classLoader;
    private final List<KnowledgeSource> sources;

    public ClasspathDocumentLoader(ClassLoader classLoader, List<KnowledgeSource> sources) {
        this.classLoader = classLoader;
        this.sources = List.copyOf(sources);
    }

    @Override
    public DocumentLoader.Loaded load() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (KnowledgeSource source : sources) {
            String path = source.path();
            if (!path.toLowerCase(Locale.ROOT).endsWith(".md") || path.contains("..") || path.startsWith("/")) {
                rejected.add(path);
                continue;
            }
            try (InputStream in = classLoader.getResourceAsStream(path)) {
                if (in == null) {
                    missing.add(path);
                    continue;
                }
                byte[] bytes = in.readNBytes((int) DocumentLoader.MAX_FILE_BYTES + 1);
                if (bytes.length > DocumentLoader.MAX_FILE_BYTES) {
                    rejected.add(path);
                    continue;
                }
                TextSanitizer.Result cleaned = TextSanitizer.clean(new String(bytes, StandardCharsets.UTF_8));
                documents.add(new KnowledgeDocument(source.id(), path, source.title(), cleaned.text(),
                        cleaned.redactions()));
            } catch (IOException | RuntimeException e) {
                rejected.add(path);
            }
        }
        return new DocumentLoader.Loaded(documents, missing, rejected);
    }

    /** 클래스패스 리소스는 실행 중 바뀌지 않으므로 경로 목록이 곧 지문이다. */
    @Override
    public String fingerprint() {
        StringBuilder sb = new StringBuilder("classpath:");
        sources.forEach(s -> sb.append(s.path()).append(';'));
        return sb.toString();
    }
}
