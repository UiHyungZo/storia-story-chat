package com.storia.backend.lore;

import com.storia.backend.rag.KnowledgeSource;
import java.util.List;
import java.util.Optional;

/**
 * 캐릭터 → 세계관 문서 매핑. DB의 자동 증가 ID에 의존하지 않는다: ID는 DB를 새로 만들면 달라질 수 있어 ID로
 * 매핑하면 다른 캐릭터의 세계관에 연결될 위험이 있다. 대신 시딩된 캐릭터 이름(예: "아리아 (Aria)")이 정확히 같을
 * 때만 해당 세계관 키로 연결한다. 관리자가 이름을 바꾸면 매핑이 끊겨 세계관이 적용되지 않는다 — 잘못된 세계관이
 * 붙는 것보다 세계관이 빠지는 쪽을 안전한 실패로 택한다.
 *
 * <p>문서는 클래스패스({@code characters/<key>/lore.md})에서 읽으므로 JAR에 포함되어 Docker 배포에서도 누락되지 않는다.
 */
public final class CharacterLoreCatalog {

    public record Entry(String loreKey, String characterName, String resourcePath, String title) {

        public KnowledgeSource source() {
            return new KnowledgeSource(loreKey, resourcePath, title);
        }
    }

    public static final CharacterLoreCatalog DEFAULT = new CharacterLoreCatalog(List.of(
            new Entry("aria", "아리아 (Aria)", "characters/aria/lore.md", "아리아 세계관"),
            new Entry("ren", "렌 (Ren)", "characters/ren/lore.md", "렌 세계관"),
            new Entry("noah", "노아 (Noah)", "characters/noah/lore.md", "노아 세계관")));

    private final List<Entry> entries;

    public CharacterLoreCatalog(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> entries() {
        return entries;
    }

    public Optional<Entry> resolve(String characterName) {
        if (characterName == null) {
            return Optional.empty();
        }
        String name = characterName.strip();
        return entries.stream().filter(e -> e.characterName().equals(name)).findFirst();
    }
}
