package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

/**
 * {@code POST /v0/translations/reports}의 본문 (명세서 §3.22, KAN-343). 번역 응답을 받은 화면이 그 요청과 응답의 값을
 * 그대로 싣는다.
 *
 * @param direction 신고하는 번역의 방향 {@code toDialect} | {@code toStandard}. 없으면 {@code toDialect}다 (번역 요청과 같다)
 * @param text      번역기에 넣었던 문장 - 앞뒤 공백을 뺀 1~100자
 * @param result    번역기가 돌려준 문장 - 앞뒤 공백을 뺀 1~300자
 */
record TranslationReportRequest(@Nullable String direction, @Nullable String text, @Nullable String result) {

    /** 값을 찍지 않는다 - 사용자가 쓴 문장이다 (§2.6). */
    @Override
    public String toString() {
        return "TranslationReportRequest[]";
    }
}
