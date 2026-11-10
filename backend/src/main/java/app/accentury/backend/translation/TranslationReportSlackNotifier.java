package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
import app.accentury.backend.feedback.FeedbackSlackNotifier;
import app.accentury.backend.feedback.SlackWebhookClient;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * 저장된 번역 신고를 슬랙 채널로 올린다 (KAN-343, 명세서 §3.22).
 * <p>
 * <b>이용 후기의 웹훅과 채널을 함께 쓴다 (2026-11-10 결정).</b> 설정({@code accentury.feedback.slack-webhook-url}),
 * 전송 클라이언트, 전용 실행기({@code feedbackNotifyExecutor})가 전부 후기의 것이다 - 운영자가 읽는 자리를 하나로
 * 두고, 웹훅을 하나 더 두는 인프라 변경을 피한다. 후기와는 메시지 머리로 가른다.
 * <p>
 * 규칙도 후기와 같다 ({@link FeedbackSlackNotifier}). 슬랙은 부수 기능이라 URL이 없거나 전송이 실패해도 신고 API는
 * 정상이고, 커밋된 뒤에만 보내며, 요청 스레드를 잡지 않는다. 신고 원본은 DB에 있다.
 */
@Component
class TranslationReportSlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(TranslationReportSlackNotifier.class);

    /** 계정 id에서 메시지에 싣는 길이 - 같은 계정의 반복 신고를 알아볼 수 있으면 된다. 전체는 DB에 있다. */
    static final int USER_ID_PREFIX_LENGTH = 8;

    private final SlackWebhookClient client;
    private final TranslationReports reports;
    private final String label;
    private final boolean enabled;

    @Autowired
    TranslationReportSlackNotifier(SlackWebhookClient client, TranslationReports reports,
                                   AccenturyProperties properties) {
        this(client, reports, properties.feedback().slackWebhookUrl(), properties.result().webTestUrl());
    }

    /** 테스트가 설정값 두 개만으로 만드는 자리. */
    TranslationReportSlackNotifier(SlackWebhookClient client, TranslationReports reports,
                                   @Nullable String slackWebhookUrl, @Nullable String webTestUrl) {
        this.client = client;
        this.reports = reports;
        this.label = FeedbackSlackNotifier.label(webTestUrl);
        this.enabled = FeedbackSlackNotifier.configured(slackWebhookUrl);
    }

    @Async("feedbackNotifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTranslationReported(TranslationReported event) {
        if (!enabled) {
            return;
        }
        try {
            // id로 다시 읽는다 - 그사이 지워졌으면(보존 기간 정리) 보내지 않는다.
            Optional<TranslationReports.Report> report = reports.find(event.reportId());
            if (report.isEmpty()) {
                return;
            }
            client.post(message(report.get(), label));
        } catch (Exception e) {
            // 예외 메시지에 웹훅 URL이 섞일 수 있다 - 마지막 관문은 로그 마스킹이다 (LogMasking의 SLACK_WEBHOOK).
            log.warn("번역 신고 슬랙 전송 실패 reportId={} cause={}", event.reportId(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 채널에 올릴 메시지 - 머리 한 줄과 인용문 두 덩이(원문, 결과)다.
     * <p>
     * 원문과 결과는 사용자가 보낸 문자열이라 슬랙 문법({@code <!channel>}, 링크)이 그대로 들어올 수 있다 - 후기 본문과
     * 같이 이스케이프한다. 계정 id는 앞자리만 싣는다: 채널은 개발팀 전원이 보고 지난 메시지가 지워지지 않는다.
     */
    static String message(TranslationReports.Report report, String label) {
        StringBuilder message = new StringBuilder()
                .append('[').append(label).append("] 번역 결과 신고 ")
                .append(report.direction())
                .append(", 계정 ").append(report.userId() == null ? "연결 끊김"
                        : report.userId().toString().substring(0, USER_ID_PREFIX_LENGTH))
                .append(", 신고 ").append(report.id());
        quote(message, "원문", report.sourceText());
        quote(message, "결과", report.resultText());
        return message.toString();
    }

    private static void quote(StringBuilder message, String title, String text) {
        message.append('\n').append(title).append(':');
        // 줄마다 인용문 접두를 붙인다 - 여러 줄 문장이 머리 줄과 섞이지 않는다.
        for (String line : FeedbackSlackNotifier.escape(text).split("\\R", -1)) {
            message.append("\n> ").append(line);
        }
    }
}
