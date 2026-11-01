package com.storia.backend.rag;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 어휘 기반(비의미) 인덱스용 근거 채택 검사: 질의의 내용어 중 하나라도 조각에 실제로 있어야 근거로 본다.
 * 해싱 벡터는 무관한 문자 n-gram 겹침만으로도 임계값을 넘을 수 있어서(예: "payment" → 배포 문서),
 * 점수 통과와 "질문에 대한 근거가 있음"을 구분하기 위한 최소 장치다. 의미 임베딩에는 쓰지 않는다
 * (한↔영 동의어처럼 표기가 달라도 맞는 근거를 버리게 된다).
 *
 * <p>한글 어절은 조사·어미를 떼기 위해 앞 2글자를, 영문·숫자는 3글자 이상 단어 전체를 내용어로 쓴다.
 * 질문 형식어(어떻게, 알려줘, 구조, 시스템 등)는 내용어에서 뺀다. 내용어가 하나도 없으면 점수 판단만 따른다.
 */
final class LexicalSupport {

    private static final Set<String> STOP_TERMS = Set.of(
            "어떻", "어떤", "무엇", "뭐야", "뭔지", "알려", "설명", "정리", "해줘", "하는", "있어", "없어", "있나", "되나",
            "방법", "이유", "구조", "동작", "기능", "시스", "관련", "대해", "현재", "지금", "사용", "스토", "프로", "문서",
            "the", "and", "for", "how", "what", "why", "does", "with", "storia", "system", "work", "works");

    private LexicalSupport() {
    }

    static Set<String> contentTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        for (String word : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            // "TestFlight로", "LiveKit으로"처럼 영문 뒤에 붙은 한글은 조사·어미이므로 어절 첫 부분이 아니면 버린다.
            String[] runs = word.split("(?<=\\p{IsHangul})(?=[^\\p{IsHangul}])|(?<=[^\\p{IsHangul}])(?=\\p{IsHangul})");
            for (int i = 0; i < runs.length; i++) {
                String run = runs[i];
                boolean hangul = hasHangul(run);
                String term = hangul
                        ? (i == 0 && run.length() >= 2 ? run.substring(0, 2) : null)
                        : (run.length() >= 3 ? run : null);
                if (term != null && !STOP_TERMS.contains(term)) {
                    terms.add(term);
                }
            }
        }
        return terms;
    }

    /** 내용어가 없으면 true(판단 보류). 있으면 그중 하나라도 조각(제목·헤딩 포함)에 나타나야 true. */
    static boolean supports(Set<String> terms, KnowledgeChunk chunk) {
        if (terms.isEmpty()) {
            return true;
        }
        String haystack = chunk.embeddingText().toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(haystack::contains);
    }

    private static boolean hasHangul(String token) {
        return token.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL);
    }
}
