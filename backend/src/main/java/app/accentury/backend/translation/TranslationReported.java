package app.accentury.backend.translation;

import java.util.UUID;

/**
 * 번역 신고 한 건이 새로 저장됐다 (KAN-343). 싣는 것은 id 하나다 - 커밋 뒤에 도는 리스너가 id로 다시 읽는다
 * ({@code FeedbackSubmitted}와 같은 이유, {@link TranslationReportSlackNotifier}).
 *
 * @param reportId {@code translation_report.id}
 */
record TranslationReported(UUID reportId) {
}
