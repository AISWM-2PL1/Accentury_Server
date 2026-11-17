package app.accentury.backend.translation;

import org.jspecify.annotations.Nullable;

/**
 * 문장 하나를 표준어와 경남 사투리 사이에서 옮기는 LLM 호출 (KAN-266, 방향은 KAN-316). 구현은
 * {@link GeminiDialectTranslator}이고, 테스트는 가짜로 바꾼다.
 * <p>
 * 돌려주는 것은 LLM의 답을 형식대로 읽은 결과뿐이다 - 그 답이 번역으로 쓸 만한지는 {@link TranslationHarness}가 다시 본다.
 */
public interface DialectTranslator {

    /**
     * @param text      서버 사전 검사를 통과한 입력 (앞뒤 공백 제거, 100자 이하)
     * @param direction 번역 방향 - 방향마다 시스템 프롬프트가 다르다
     * @throws Unavailable 호출 실패, 시간 초과, 무료 한도 소진(429), 키 없음. 요청이 Gemini에 닿았는지는
     *                     {@link Unavailable#consumed()}가 말한다 (KAN-361)
     */
    Reply translate(String text, TranslationDirection direction);

    /** 호출하는 모델 이름 - 번역 기록의 {@code model} 필드다. */
    String model();

    /**
     * LLM을 부를 수 있게 설정됐는가 - API 키가 없는 배포의 번역기만 false다 (KAN-361). 서비스는 false면 한도 칸을 잡기 전에
     * 503으로 끝낸다. 부르지도 않은 LLM 호출을 호출 수(KAN-322)에 세면, 키 없는 환경에서 503을
     * {@value app.accentury.backend.common.AccenturyProperties.Translation#DAILY_CALL_LIMIT}번 받은 뒤 그날은 429로 바뀐다.
     * <p>
     * 기본 구현을 두지 않는다 - 다른 구현을 감싸는 번역기가 생기면 감싼 쪽의 값을 직접 넘겨야 한다. 기본값 true였다면 키 없는
     * 번역기를 감싼 래퍼가 「설정됨」으로 보인다 (PR #77 리뷰).
     */
    boolean configured();

    /**
     * LLM의 답.
     *
     * @param kind   답의 종류
     * @param output {@link Kind#TRANSLATED}일 때의 번역 문장 (방향에 따라 사투리 또는 표준어). 그 밖에는 null
     */
    record Reply(Kind kind, @Nullable String output) {

        static Reply translated(String output) {
            return new Reply(Kind.TRANSLATED, output);
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

    /**
     * LLM을 부르지 못했다 - 503 {@code TRANSLATION_UNAVAILABLE}. 메시지에 입력 텍스트를 넣지 않는다.
     * <p>
     * {@link #consumed()}는 이 호출이 계정의 하루 호출 수(KAN-322)에 남아야 하는가다 (KAN-361). 요청이 Gemini에 닿았을 수
     * 있으면(시간 초과, 5xx, 무료 한도 429, 응답 뒤의 실패) true이고, 닿지 않았거나(요청 생성 실패, 연결 실패) 키, 모델 이름
     * 같은 설정 오류(400, 401, 403, 404)면 false다. false면 서비스가 호출 수를 되돌린다 - 설정 오류로 계정이 그날 429로
     * 잠기고 설정을 고쳐도 풀리지 않던 것을 막는다.
     */
    final class Unavailable extends RuntimeException {

        private final boolean consumed;

        public Unavailable(String reason) {
            this(reason, true);
        }

        public Unavailable(String reason, boolean consumed) {
            super(reason);
            this.consumed = consumed;
        }

        /** 이 실패가 하루 호출 수에 남아야 하는가. */
        public boolean consumed() {
            return consumed;
        }
    }
}
