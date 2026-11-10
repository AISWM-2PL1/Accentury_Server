package app.accentury.backend.translation;

import java.util.UUID;

/**
 * {@code POST /v0/translations/reports} 201 응답 (명세서 §3.22).
 *
 * @param reportId 저장된 신고의 id - 사용자가 문의할 때 운영자가 신고를 찾는 값이다
 */
record TranslationReportResponse(UUID reportId) {
}
