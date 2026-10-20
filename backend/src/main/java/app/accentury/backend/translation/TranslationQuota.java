package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;

import java.util.UUID;

/**
 * 번역 일일 한도 - 계정당 하루 {@value AccenturyProperties.Translation#DAILY_LIMIT}건, 성공한 번역만 센다 (KAN-266, 명세서 §3.18,
 * 2026-10-15 결정). 구현은 {@link RedisTranslationQuota}이고, 테스트는 가짜로 바꿔 끼운다.
 * <p>
 * LLM을 부르기 전에 칸 하나를 먼저 잡고({@link #acquire}), 성공이 아니면 돌려준다({@link #release}). 확인만 하고 성공한 뒤에
 * 세면 동시 요청이 함께 확인을 통과해 하루 한도를 넘는다.
 */
interface TranslationQuota {

    /**
     * 오늘 칸 하나를 잡는다.
     *
     * @throws Exhausted        오늘 칸이 없다 - 429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}
     * @throws StoreUnavailable 카운터 저장소에 닿지 못했다 - 한도를 지킬 수 없어 503 {@code TRANSLATION_UNAVAILABLE}
     */
    Slot acquire(UUID userId);

    /** 잡은 칸을 돌려준다 - 번역이 성공하지 못했을 때. 실패해도 예외를 내지 않는다 (그 칸은 그날 자정까지 잃는다). */
    void release(Slot slot);

    /**
     * 번역이 성공한 지금 그날 남은 건수 - 200 응답의 {@code remaining}이다. 잡을 때의 값({@link Slot#used})은 같은 계정의
     * 동시 요청이 칸을 돌려주거나 더 잡으면 낡으므로 지금 다시 읽는다 (Codex 리뷰 P2). 아직 끝나지 않은 다른 요청이 잡은
     * 칸은 쓴 것으로 본다. 다시 읽지 못하면 잡을 때의 값으로 계산한다 - 번역은 이미 성공했으니 실패시키지 않는다.
     */
    int remaining(Slot slot);

    /**
     * 잡은 칸.
     *
     * @param key  카운터 키 - 자정을 넘겨 돌려줘도 잡은 날의 카운터를 돌려주도록 잡을 때의 키를 들고 다닌다
     * @param used 이 칸을 포함해 오늘 쓴 건수
     */
    record Slot(String key, int used) {

        /** 잡을 때 기준으로 남은 건수 - {@link TranslationQuota#remaining}이 다시 읽지 못했을 때 쓴다. */
        int remaining() {
            return remainingAfter(used);
        }

        static int remainingAfter(long used) {
            return (int) Math.max(AccenturyProperties.Translation.DAILY_LIMIT - used, 0);
        }
    }

    /** 오늘 칸이 없다. */
    final class Exhausted extends RuntimeException {
        Exhausted() {
            super("번역 일일 한도 소진", null, false, false);
        }
    }

    /** 카운터 저장소 장애. */
    final class StoreUnavailable extends RuntimeException {
        StoreUnavailable(String message) {
            super(message, null, false, false);
        }
    }
}
