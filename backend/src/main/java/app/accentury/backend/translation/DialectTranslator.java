package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

/**
 * 표준어 문장 하나를 경남 사투리로 옮기는 LLM 호출 (KAN-266). 구현은 {@link GeminiDialectTranslator}이고, 테스트는 가짜로
 * 바꾼다.
 * <p>
 * 돌려주는 것은 LLM의 답을 형식대로 읽은 결과뿐이다 - 그 답이 번역으로 쓸 만한지는 {@link TranslationHarness}가 다시 본다.
 */
public interface DialectTranslator {

    /**
     * @param text 서버 사전 검사를 통과한 입력 (앞뒤 공백 제거, 100자 이하)
     * @throws Unavailable 호출 실패, 시간 초과, 무료 한도 소진(429), 키 없음
     */
    Reply translate(String text);

    /** 호출하는 모델 이름 - 번역 기록의 {@code model} 필드다. */
    String model();

    /**
     * LLM의 답.
     *
     * @param kind    답의 종류
     * @param dialect {@link Kind#TRANSLATED}일 때의 번역 문장. 그 밖에는 null
     */
    record Reply(Kind kind, @Nullable String dialect) {

        static Reply translated(String dialect) {
            return new Reply(Kind.TRANSLATED, dialect);
        }

        static Reply of(Kind kind) {
            return new Reply(kind, null);
        }
    }

    enum Kind {
        /** 번역했다고 답했다. */
        TRANSLATED,
        /** 하네스 규칙에 따라 번역을 거절했다 ({@code status: REJECTED}). */
        REFUSED,
        /** 사업자 안전 필터가 입력이나 출력을 막았다. */
        BLOCKED,
        /** 답이 약속한 JSON 형식이 아니다. */
        MALFORMED
    }

    /** LLM을 부르지 못했다 - 503 {@code TRANSLATION_UNAVAILABLE}. 메시지에 입력 텍스트를 넣지 않는다. */
    final class Unavailable extends RuntimeException {

        public Unavailable(String reason) {
            super(reason);
        }
    }
}
