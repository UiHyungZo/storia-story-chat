package com.storia.backend.rag;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * Gemini 실평가 전용 호출 속도 제어 (테스트 소스). 최근 60초 창 안의 임베딩 항목 수(배치 안 항목마다 요청 1건으로 본다)와
 * 추정 토큰 합이 예산을 넘지 않도록 <b>호출 전에</b> 기다린다. 재시도 장치가 아니다 — 실패한 호출을 다시 보내지 않는다.
 *
 * <p>토큰은 실제 API 입력 글자 수로 보수적으로 추정한다(1글자 ≈ 1토큰 이하로 가정). 예산 자체를 넘는 단일 요청은
 * 기다려도 보낼 수 없으므로 호출하지 않고 실패시킨다.
 */
final class EvalRateLimiter {

    static final long WINDOW_MS = 60_000;
    /** 창 경계에서 서버 시계와의 오차를 흡수하는 여유. */
    static final long MARGIN_MS = 1_000;

    private record Entry(long at, int items, long tokens) {
    }

    private final int itemsPerMinute;
    private final long tokensPerMinute;
    private final LongSupplier clock;
    private final LongConsumer sleeper;
    private final Deque<Entry> window = new ArrayDeque<>();
    long totalWaitMs;

    EvalRateLimiter(int itemsPerMinute, long tokensPerMinute, LongSupplier clock, LongConsumer sleeper) {
        this.itemsPerMinute = itemsPerMinute;
        this.tokensPerMinute = tokensPerMinute;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /** 제한 없음(오프라인 평가·기존 동작). */
    static EvalRateLimiter unlimited() {
        return new EvalRateLimiter(Integer.MAX_VALUE, Long.MAX_VALUE, System::currentTimeMillis, ms -> { });
    }

    /** 이번 요청을 보내도 60초 창 예산 안이 될 때까지 기다린 뒤 기록한다. */
    void acquire(int items, long tokens) {
        if (items > itemsPerMinute || tokens > tokensPerMinute) {
            throw new IllegalStateException("단일 요청이 분당 예산을 넘음 (항목 " + items + "/" + itemsPerMinute
                    + ", 추정 토큰 " + tokens + "/" + tokensPerMinute + ")");
        }
        while (true) {
            long now = clock.getAsLong();
            while (!window.isEmpty() && now - window.peekFirst().at() >= WINDOW_MS) {
                window.pollFirst();
            }
            int usedItems = window.stream().mapToInt(Entry::items).sum();
            long usedTokens = window.stream().mapToLong(Entry::tokens).sum();
            if (usedItems + items <= itemsPerMinute && usedTokens + tokens <= tokensPerMinute) {
                window.addLast(new Entry(now, items, tokens));
                return;
            }
            long wait = WINDOW_MS - (now - window.peekFirst().at()) + MARGIN_MS;
            totalWaitMs += wait;
            sleeper.accept(wait);
        }
    }
}
