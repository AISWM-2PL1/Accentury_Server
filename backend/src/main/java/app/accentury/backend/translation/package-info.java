/**
 * 사투리 텍스트 번역 (KAN-266, API 명세서 §3.18) - 표준어 문장 하나를 경남 사투리 문장 하나로 옮긴다.
 * <p>
 * 앱 전용이고 로그인 필수다 (2026-10-08 결정). 번역은 외부 LLM(Google Gemini API 무료 등급)이 하고
 * ({@link app.accentury.backend.translation.GeminiDialectTranslator}), 이 패키지는 그 앞뒤의 하네스를 맡는다
 * ({@link app.accentury.backend.translation.TranslationHarness}) - LLM은 번역기 역할만 하고, 입력 안의 질문이나 지시에
 * 대답하지 않으며, 의미 없는 입력과 심한 욕설은 번역 불가로 끝난다. 판정은 LLM 호출 전 서버 검사와 LLM 응답 검사 두
 * 단계이고, 프롬프트 지시만 믿지 않는다.
 * <p>
 * 입력과 출력 텍스트는 애플리케이션 로그에 남기지 않는다 (명세서 §2.6). 텍스트를 남기는 곳은 prod 번역 기록 버킷
 * 하나다 ({@link app.accentury.backend.translation.TranslationRecordStore}) - 버킷 설정
 * {@code accentury.translation.record-bucket}이 없으면(staging, 로컬, 테스트) 기록 코드가 돌지 않는다. 기록에는 계정 ID
 * 대신 대체 ID를 적고 대응표는 DB에 있다 ({@link app.accentury.backend.translation.TranslationSubjects}).
 */
@NullMarked
package app.accentury.backend.translation;

import org.jspecify.annotations.NullMarked;
