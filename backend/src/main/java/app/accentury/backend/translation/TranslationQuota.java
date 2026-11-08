package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;

import java.util.UUID;

/**
 * 번역 일일 한도 - 계정당 하루 {@value AccenturyProperties.Translation#DAILY_LIMIT}건, 성공한 번역만 센다 (KAN-266, 명세서 §3.18,
 * 2026-10-15 결정). 구현은 {@link RedisTranslationQuota}이고, 테스트는 가짜로 바꿔 끼운다.
 * <p>
 * LLM을 부르기 전에 칸 하나를 먼저 잡고({@link #acquire}), 성공이 아니면 돌려준다({@link #release}). 확인만 하고 성공한 뒤에
 * 세면 동시 요청이 함께 확인을 통과해 하루 한도를 넘는다.
 * <p>
 * 칸을 잡을 때 LLM 호출 수도 함께 센다 - 계정당 하루 {@value AccenturyProperties.Translation#DAILY_CALL_LIMIT}번이고 결과와
 * 상관없이 돌려주지 않는다 (KAN-322). 칸만 세면 LLM이 거절하는 문장의 반복이 한도에 걸리지 않는다.
 */
interface TranslationQuota {

    /**
     * 오늘 칸 하나를 잡고 호출 수를 하나 늘린다.
     *
     * @throws Exhausted        오늘 칸이 없거나 호출 수 상한에 닿았다 - 429 {@code TRANSLATION_DAILY_LIMIT_EXCEEDED}
     * @throws StoreUnavailable 카운터 저장소에 닿지 못했다 - 한도를 지킬 수 없어 503 {@code TRANSLATION_UNAVAILABLE}
     */
    Slot acquire(UUID userId);

    /**
     * 잡은 칸을 돌려준다 - 번역이 성공하지 못했을 때. 호출 수는 돌려주지 않는다. 실패해도 예외를 내지 않는다 (그 칸은 그날 자정까지
     * 잃는다).
     */
    void release(Slot slot);

    /**
     * 번역이 성공한 지금 그날 더 성공할 수 있는 건수 - 200 응답의 {@code remaining}이다. 남은 성공 칸과 남은 호출 수 가운데 작은
     * 쪽이다 (PR #66 리뷰) - 성공마다 호출 하나를 쓰므로 호출이 먼저 바닥나면 칸이 남아도 다음 요청은 429다. 잡을 때의 값
     * ({@link Slot#used})은 같은 계정의 동시 요청이 칸을 돌려주거나 더 잡으면 낡으므로 지금 다시 읽는다 (Codex 리뷰 P2). 아직
     * 끝나지 않은 다른 요청이 잡은 칸은 쓴 것으로 본다. 다시 읽지 못하면 잡을 때의 성공 칸 값으로 계산한다 - 번역은 이미
     * 성공했으니 실패시키지 않는다.
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

        /** 호출 수 기준으로 남은 건수 - 성공마다 호출 하나를 쓰므로 더 성공할 수 있는 건수의 상한이다. */
        static int callsRemainingAfter(long calls) {
            return (int) Math.max(AccenturyProperties.Translation.DAILY_CALL_LIMIT - calls, 0);
        }
    }

    /** 오늘 칸이 없거나 호출 수 상한에 닿았다. */
    final class Exhausted extends RuntimeException {

        private final boolean callLimit;

        Exhausted() {
            this(false);
        }

        Exhausted(boolean callLimit) {
            super("번역 일일 한도 소진", null, false, false);
            this.callLimit = callLimit;
        }

        /** 성공 칸이 아니라 호출 수 상한에 닿았는가 - 429의 문구가 다르다 (PR #66 리뷰). */
        boolean callLimit() {
            return callLimit;
        }
    }

    /** 카운터 저장소 장애. */
    final class StoreUnavailable extends RuntimeException {
        StoreUnavailable(String message) {
            super(message, null, false, false);
        }
    }
}
