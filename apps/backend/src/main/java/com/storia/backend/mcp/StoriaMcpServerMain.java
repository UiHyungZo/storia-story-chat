package com.storia.backend.mcp;

import com.storia.backend.rag.CachingEmbeddingProvider;
import com.storia.backend.rag.DocumentLoader;
import com.storia.backend.rag.EmbeddingProvider;
import com.storia.backend.rag.GeminiEmbeddingProvider;
import com.storia.backend.rag.KnowledgeBase;
import com.storia.backend.rag.KnowledgeSearchService;
import com.storia.backend.rag.KnowledgeSources;
import com.storia.backend.rag.LocalHashingEmbeddingProvider;
import com.storia.backend.rag.MarkdownChunker;
import com.storia.backend.rag.RagMetrics;
import io.modelcontextprotocol.server.McpSyncServer;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Storia MCP 서버 (STDIO). Spring/DB/관리자 세션을 쓰지 않는 별도 프로세스이며 클라이언트(예: Claude Desktop,
 * MCP Inspector)가 하위 프로세스로 실행한다.
 *
 * <p>stdout은 MCP 메시지 전용이다: 시작하자마자 {@code System.out}을 stderr로 돌려 로그나 실수로 찍힌 출력이
 * 프로토콜 스트림을 오염시키지 못하게 하고, 프로토콜은 원래의 stdout 스트림으로만 주고받는다.
 *
 * <p>접근 범위: 서버는 실행한 OS 사용자의 권한으로 동작하고 네트워크 포트를 열지 않는다. 읽는 것은 허용 목록
 * 문서({@link KnowledgeSources})뿐이며 관리자 세션·DB·쓰기 기능이 없다.
 *
 * <p>환경변수: {@code STORIA_DOCS_ROOT}(기본: cwd에서 위로 올라가며 찾은 저장소 루트), {@code STORIA_RAG_PROVIDER}
 * ({@code local}(기본) | {@code gemini}), {@code GEMINI_API_KEY}, {@code STORIA_RAG_GEMINI_MODEL},
 * {@code STORIA_RAG_DIMENSIONS}, {@code STORIA_RAG_CACHE_DIR}, {@code STORIA_RAG_MIN_SCORE}.
 */
public final class StoriaMcpServerMain {

    private StoriaMcpServerMain() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream protocolOut = System.out;
        System.setOut(System.err);

        KnowledgeSearchService service = buildService();
        McpToolMetrics metrics = new McpToolMetrics(System.err);
        McpSyncServer server = StoriaMcpServer.build(new KnowledgeMcpTool(service, metrics), eofSignaling(), protocolOut);
        System.err.println("storia-mcp-server ready (" + StoriaMcpServer.SERVER_NAME + " " + StoriaMcpServer.SERVER_VERSION + ")");

        STDIN_CLOSED.await();
        server.closeGracefully();
    }

    private static final CountDownLatch STDIN_CLOSED = new CountDownLatch(1);

    /** stdin이 닫히면(클라이언트 종료) 프로세스도 종료되도록 EOF를 감지한다. */
    private static InputStream eofSignaling() {
        return new FilterInputStream(System.in) {
            @Override
            public int read() throws IOException {
                int b = super.read();
                if (b < 0) {
                    STDIN_CLOSED.countDown();
                }
                return b;
            }

            @Override
            public int read(byte[] buffer, int off, int len) throws IOException {
                int n = super.read(buffer, off, len);
                if (n < 0) {
                    STDIN_CLOSED.countDown();
                }
                return n;
            }
        };
    }

    static KnowledgeSearchService buildService() {
        Path root = env("STORIA_DOCS_ROOT") != null ? Path.of(env("STORIA_DOCS_ROOT")) : findRepoRoot(Path.of("").toAbsolutePath());
        EmbeddingProvider provider;
        if ("gemini".equalsIgnoreCase(env("STORIA_RAG_PROVIDER"))) {
            String model = env("STORIA_RAG_GEMINI_MODEL") != null ? env("STORIA_RAG_GEMINI_MODEL") : "gemini-embedding-2";
            int dimensions = env("STORIA_RAG_DIMENSIONS") != null ? Integer.parseInt(env("STORIA_RAG_DIMENSIONS")) : 768;
            String cache = env("STORIA_RAG_CACHE_DIR") != null ? env("STORIA_RAG_CACHE_DIR") : ".storia-rag-cache";
            provider = new CachingEmbeddingProvider(new GeminiEmbeddingProvider(env("GEMINI_API_KEY"),
                    "https://generativelanguage.googleapis.com", model, dimensions), Path.of(cache));
        } else {
            provider = new LocalHashingEmbeddingProvider();
        }
        Double minScore = env("STORIA_RAG_MIN_SCORE") != null ? Double.valueOf(env("STORIA_RAG_MIN_SCORE")) : null;
        KnowledgeBase base = new KnowledgeBase(new DocumentLoader(root, KnowledgeSources.DEFAULT), provider,
                new MarkdownChunker(800), minScore);
        System.err.println("storia-mcp-server docsRoot=" + root + " embedding=" + provider.id());
        return new KnowledgeSearchService(base, new RagMetrics(), true);
    }

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? null : v;
    }

    /** cwd부터 위로 올라가며 AGENTS.md와 docs/가 함께 있는 디렉터리를 찾는다. 못 찾으면 cwd. */
    static Path findRepoRoot(Path start) {
        for (Path p = start; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("AGENTS.md")) && Files.isDirectory(p.resolve("docs"))) {
                return p;
            }
        }
        return start;
    }
}
