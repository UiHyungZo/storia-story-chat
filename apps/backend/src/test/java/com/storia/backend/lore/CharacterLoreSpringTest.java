package com.storia.backend.lore;

import static org.assertj.core.api.Assertions.assertThat;

import com.storia.backend.entity.Character;
import com.storia.backend.repository.CharacterRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 Spring 컨텍스트(H2 + CharacterSeeder)에서 DB가 발급한 ID로 올바른 세계관에 연결되는지 확인한다. 외부 호출 없음. */
@SpringBootTest(properties = {"storia.character-rag.enabled=true", "storia.character-rag.provider=local"})
class CharacterLoreSpringTest {

    @Autowired private CharacterLoreService service;
    @Autowired private CharacterRepository characters;

    @Test
    void seededCharactersMapToTheirOwnLoreViaDatabaseIds() {
        Map<String, String> question = Map.of(
                "아리아 (Aria)", "초광속 도약은 하루에 몇 번 할 수 있어?",
                "렌 (Ren)", "서점은 무슨 요일에 쉬어요?",
                "노아 (Noah)", "괘종시계는 왜 멈췄어?");
        Map<String, String> key = Map.of("아리아 (Aria)", "aria", "렌 (Ren)", "ren", "노아 (Noah)", "noah");

        assertThat(service.enabled()).isTrue();
        for (Character c : characters.findAll()) {
            CharacterLoreResult r = service.searchCharacterLore(c.getId(), question.get(c.getName()), 3);
            assertThat(r.status()).as(c.getName()).isEqualTo(LoreStatus.OK);
            assertThat(r.loreKey()).isEqualTo(key.get(c.getName()));
            assertThat(r.hits()).allSatisfy(h -> assertThat(h.docId()).isEqualTo(key.get(c.getName())));
        }
        assertThat(characters.count()).isEqualTo(3);
    }
}
