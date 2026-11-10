package app.accentury.backend.translation;

import app.accentury.backend.common.AccenturyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 번역 신고의 보존 기간 정리 (KAN-343, 명세서 §5.5 - 1년, 2026-11-10 결정).
 * <p>
 * 신고에는 사용자가 번역기에 넣은 문장이 들어 있다. 탈퇴가 계정과의 연결을 끊어도 문장은 남으므로 기한을 둔다 -
 * 이용 후기와 같은 1년이다 ({@code FeedbackRetention}). 방침(privacy.html 1항 표의 「번역 신고 기록 (앱)」)이 이 기간을
 * 약속한다.
 */
@Component
class TranslationReportRetention {

    private static final Logger log = LoggerFactory.getLogger(TranslationReportRetention.class);

    private final TranslationReports reports;
    private final Clock clock;

    @Autowired
    TranslationReportRetention(TranslationReports reports) {
        this(reports, Clock.systemUTC());
    }

    /** 테스트가 시각을 고정하는 자리 - 경계(정확히 보존 기간 전)는 고정 시계여야 단언할 수 있다. */
    TranslationReportRetention(TranslationReports reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    /** 다른 정리 잡(분석 15분, 어휘 25분, 결과 35분, 웹훅 45분, 후기 55분 지연)과 시작 시점만 어긋나게 둔다. */
    @Scheduled(initialDelay = 50, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    void purgeExpired() {
        // 경계는 cutoff 미만이다 - 정확히 보존 기간 전에 들어온 행은 아직 남는다.
        Instant cutoff = clock.instant().minus(AccenturyProperties.Translation.REPORT_RETENTION);
        int removed = reports.deleteCreatedBefore(cutoff);
        if (removed > 0) {
            log.info("보존 기간이 지난 번역 신고 {}건 삭제", removed);
        }
    }
}
