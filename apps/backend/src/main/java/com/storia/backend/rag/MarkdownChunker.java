package com.storia.backend.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown을 헤딩 단위로 나눈 뒤 문단 경계에서 {@code maxChars} 이하로 자른다. 코드 펜스 안의 '#'은
 * 헤딩으로 취급하지 않는다. 같은 헤딩 안에서 생긴 너무 짧은 조각(&lt; {@value #MIN_CHARS}자)만 앞 조각에 합친다
 * (다른 헤딩의 조각은 헤딩 정보를 잃지 않도록 합치지 않는다).
 */
public final class MarkdownChunker {

    static final int MIN_CHARS = 40;
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*$");

    private final int maxChars;

    public MarkdownChunker(int maxChars) {
        if (maxChars < 200) {
            throw new IllegalArgumentException("maxChars는 200 이상이어야 합니다.");
        }
        this.maxChars = maxChars;
    }

    public List<KnowledgeChunk> chunk(KnowledgeDocument doc) {
        List<String[]> sections = new ArrayList<>(); // [heading path, body]
        String[] headings = new String[7];
        StringBuilder body = new StringBuilder();
        String currentPath = doc.title();
        boolean inFence = false;

        for (String line : doc.text().split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                inFence = !inFence;
            }
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                flush(sections, currentPath, body);
                int level = m.group(1).length();
                headings[level] = m.group(2).strip();
                for (int i = level + 1; i < headings.length; i++) {
                    headings[i] = null;
                }
                StringBuilder path = new StringBuilder();
                for (int i = 1; i < headings.length; i++) {
                    if (headings[i] != null) {
                        path.append(path.length() == 0 ? "" : " > ").append(headings[i]);
                    }
                }
                currentPath = path.toString();
            } else {
                body.append(line).append('\n');
            }
        }
        flush(sections, currentPath, body);

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (String[] section : sections) {
            for (String piece : split(section[1])) {
                if (piece.length() < MIN_CHARS && !chunks.isEmpty()
                        && chunks.get(chunks.size() - 1).heading().equals(section[0])) {
                    KnowledgeChunk prev = chunks.remove(chunks.size() - 1);
                    chunks.add(build(doc, prev.chunkId(), prev.heading(), prev.text() + "\n\n" + piece));
                } else {
                    chunks.add(build(doc, doc.id() + "#" + chunks.size(), section[0], piece));
                }
            }
        }
        return chunks;
    }

    private void flush(List<String[]> sections, String path, StringBuilder body) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new String[] {path, text});
        }
        body.setLength(0);
    }

    private List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\n\\s*\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > maxChars) {
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                pieces.addAll(hardSplit(p));
            } else if (current.length() + p.length() + 2 > maxChars) {
                pieces.add(current.toString());
                current.setLength(0);
                current.append(p);
            } else {
                current.append(current.length() == 0 ? "" : "\n\n").append(p);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    /** 긴 문단은 줄 단위로, 그래도 길면 공백 기준으로 자른다. */
    private List<String> hardSplit(String paragraph) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : paragraph.split("\n")) {
            String l = line.stripTrailing();
            if (l.length() > maxChars) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                for (int start = 0; start < l.length(); ) {
                    int end = Math.min(l.length(), start + maxChars);
                    if (end < l.length()) {
                        int space = l.lastIndexOf(' ', end);
                        if (space > start + maxChars / 2) {
                            end = space;
                        }
                    }
                    out.add(l.substring(start, end).strip());
                    start = end;
                }
            } else if (current.length() + l.length() + 1 > maxChars) {
                out.add(current.toString());
                current.setLength(0);
                current.append(l);
            } else {
                current.append(current.length() == 0 ? "" : "\n").append(l);
            }
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private KnowledgeChunk build(KnowledgeDocument doc, String chunkId, String heading, String text) {
        String embeddingText = doc.title() + (heading.isBlank() || heading.equals(doc.title()) ? "" : " > " + heading)
                + "\n" + text;
        return new KnowledgeChunk(chunkId, doc.id(), doc.source(), doc.title(), heading, text, embeddingText,
                sha256(embeddingText));
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
