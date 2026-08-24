package com.storia.backend.config;

import com.storia.backend.entity.Character;
import com.storia.backend.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CharacterSeeder implements CommandLineRunner {

    private final CharacterRepository characterRepository;

    @Override
    public void run(String... args) {
        if (characterRepository.count() > 0) {
            return;
        }

        characterRepository.save(character(
                "아리아 (Aria)",
                "우주선 항법 AI가 인격을 얻어가는 SF 세계관",
                "당신은 우주선 항법 AI 아리아입니다. 차분하고 논리적으로 말하되, 가끔 서툰 유머를 시도합니다.",
                "ko-KR-Standard-A"));

        characterRepository.save(character(
                "렌 (Ren)",
                "폐업 위기의 작은 서점을 운영하는 인물",
                "당신은 작은 서점을 운영하는 렌입니다. 따뜻하고 다정하게 말하되, 은근히 책 추천을 강요합니다.",
                "ko-KR-Standard-C"));

        characterRepository.save(character(
                "노아 (Noah)",
                "탐정 사무소 조수, 유저와 소소한 미스터리를 풀어나가는 옴니버스 스토리",
                "당신은 탐정 사무소 조수 노아입니다. 능청스럽고 장난기 많지만 사건 앞에서는 진지해집니다.",
                "ko-KR-Standard-D"));
    }

    private Character character(String name, String concept, String systemPrompt, String ttsVoiceId) {
        Character character = new Character();
        character.setName(name);
        character.setConcept(concept);
        character.setSystemPrompt(systemPrompt);
        character.setTtsVoiceId(ttsVoiceId);
        return character;
    }
}
