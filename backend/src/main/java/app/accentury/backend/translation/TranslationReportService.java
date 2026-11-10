package app.accentury.backend.translation;

import app.accentury.backend.auth.AppUser;
import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.common.RateLimits;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * 번역 결과 신고 (KAN-343, 명세서 §3.22, App Store 심사 지침 1.2).
 * <p>
 * 번역 결과는 외부 LLM이 만든다. 부적절한 결과를 본 사용자가 알릴 수 있어야 하고, 운영자는 저장된 신고를 슬랙 알림으로
 * 본다. 신고는 번역 요청과 응답의 값을 클라이언트가 그대로 다시 보낸 것이다 - 서버에는 신고가 가리킬 번역 기록이
 * 없다(staging은 기록을 남기지 않고, prod의 기록은 계정 대신 대체 ID만 적힌 S3 객체다). 그래서 신고의 원문과 결과가
 * 실제로 번역기가 낸 값인지는 서버가 확인하지 않는다. 신고는 운영자가 읽는 제보이고 자동으로 무엇을 막는 데 쓰지 않는다.
 * <p>
 * 순서: 형식 검사(400) → 분당 제한(429, IP 축 뒤에 계정 축) → 저장 → 슬랙 알림(커밋 뒤, 비동기). 원문과 결과는 로그에 남기지 않는다 (§2.6).
 */
@Service
class TranslationReportService {

    private static final Logger log = LoggerFactory.getLogger(TranslationReportService.class);

    private final TranslationReports reports;
    private final RateLimits rateLimits;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher publisher;

    TranslationReportService(TranslationReports reports, RateLimits rateLimits,
                             TransactionTemplate transactionTemplate, ApplicationEventPublisher publisher) {
        this.reports = reports;
        this.rateLimits = rateLimits;
        this.transactionTemplate = transactionTemplate;
        this.publisher = publisher;
    }

    /**
     * @throws ApiException 400 {@code VALIDATION_FAILED} / 401 {@code AUTH_TOKEN_INVALID} - 그사이 탈퇴한 계정 /
     *                      429 {@code RATE_LIMITED}
     */
    TranslationReportResponse report(AppUser user, @Nullable TranslationReportRequest request, String clientIp) {
        if (request == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 본문이 필요합니다.");
        }
        TranslationDirection direction = direction(request.direction());
        String text = required(request.text(), "text", AccenturyProperties.Translation.MAX_INPUT_LENGTH);
        String result = required(request.result(), "result", AccenturyProperties.Translation.MAX_REPORT_RESULT_LENGTH);
        // IP가 먼저다 (PR #70 리뷰) - 계정 축만 있으면 게스트 계정을 새로 만들 때마다 한도가 새로 생겨, 한 IP가 계정 수만큼
        // 슬랙 채널에 메시지를 보낼 수 있다.
        rateLimits.check(RateLimits.Scope.TRANSLATION_REPORT_IP, clientIp);
        rateLimits.check(RateLimits.Scope.TRANSLATION_REPORT, user.id().toString());

        UUID reportId = UUID.randomUUID();
        Boolean saved = transactionTemplate.execute(tx -> {
            if (!reports.save(reportId, user.id(), direction, text, result, Instant.now())) {
                return false;
            }
            // 트랜잭션 안에서 발행해야 커밋 뒤 리스너(AFTER_COMMIT)가 받는다 - 밖에서 발행하면 조용히 버려진다.
            publisher.publishEvent(new TranslationReported(reportId));
            return true;
        });
        if (!Boolean.TRUE.equals(saved)) {
            // 인증과 저장 사이에 탈퇴했다 - 탈퇴한 계정의 id를 단 신고를 남기지 않는다.
            throw new ApiException(ErrorCode.AUTH_TOKEN_INVALID);
        }
        // 내용은 남기지 않는다 - 길이만.
        log.info("번역 신고 저장 reportId={} userId={} direction={} textLength={} resultLength={}", reportId, user.id(),
                direction.apiValue(), TranslationHarness.length(text), TranslationHarness.length(result));
        return new TranslationReportResponse(reportId);
    }

    private static TranslationDirection direction(@Nullable String value) {
        if (value == null) {
            return TranslationDirection.TO_DIALECT;
        }
        return TranslationDirection.fromApiValue(value).orElseThrow(() -> new ApiException(
                ErrorCode.VALIDATION_FAILED, "direction은 toDialect, toStandard 중 하나여야 합니다."));
    }

    /** 앞뒤 공백을 뺀 1~{@code max}자 - 길이는 번역 입력과 같이 코드 포인트로 센다. */
    private static String required(@Nullable String value, String field, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || TranslationHarness.length(stripped) > max) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + "는 1~" + max + "자여야 합니다.");
        }
        if (stripped.indexOf('\u0000') >= 0) {
            // PostgreSQL은 문자열에 NUL을 받지 않는다 (PR #70 리뷰) - 여기서 거르지 않으면 저장에서 터져 400이 아니라 500이 된다.
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + "에 쓸 수 없는 문자가 있습니다.");
        }
        return stripped;
    }
}
