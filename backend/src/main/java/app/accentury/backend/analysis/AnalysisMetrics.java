package app.accentury.backend.analysis;

import app.accentury.backend.common.ErrorCode;
import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 분석 파이프라인의 지표 (KAN-38).
 * <p>
 * 큐가 없는 구조라(BE에서 AI로 HTTP 디스패치, {@link HttpAnalysisDispatcher}) "큐 적체"에
 * 해당하는 지표가 없다. 대신 <b>진행 중 건수</b>가 곧 AI에 걸린 압력이고, 그것을 두 눈금으로
 * 낸다 - 전 인스턴스 합({@link AnalysisCongestion}, DB의 PROCESSING 행 수)과 이 인스턴스의
 * 몫({@link AnalysisBacklog})이다. 경보는 전자를 본다: 태스크가 여럿이면(KAN-168) 후자는
 * 전체 밀림을 과소 판정한다. 후자를 함께 내는 것은 "AI가 밀리는가"와 "이 태스크가 밀리는가"를
 * 가르기 위해서다 - 둘이 벌어지면 ALB 분배나 워커 풀 쪽을 본다.
 * <p>
 * 회로 상태({@code accentury.ai.circuit.state})는 여기 없다 - 회로 차단기를 조립하는
 * {@link AnalysisDispatchConfig}가 KAN-36에서 이미 등록했고, 그 수명이 곧 게이지의 수명이다.
 */
@Component
public class AnalysisMetrics {

    private final Timer duration;
    private final Counter stuckTimeouts;
    private final Counter lostTimeouts;
    private final Counter congestedPolls;
    private final Counter normalPolls;
    private final Counter unscorableJudged;
    private final Counter misreadJudged;
    private final Counter otherJudged;

    AnalysisMetrics(MeterRegistry meterRegistry, AnalysisCongestion congestion, AnalysisBacklog backlog) {
        // 게이지는 발행 주기마다 한 번 읽힌다 (배포에서 1분). 전 인스턴스 값은 DB count 한 번인데
        // 그 조회에는 이미 1초 캐시가 있어(AnalysisCongestion) 폴링 경로와 값을 공유한다 -
        // 지표 때문에 count가 더 나가지는 않는다.
        Gauge.builder(ServiceMetrics.ANALYSIS_PROCESSING, congestion, AnalysisCongestion::processingJobs)
                .description("전 인스턴스의 진행 중 분석 작업 수 - 큐가 없으므로 이 값이 병목 지표다 (KAN-38)")
                .register(meterRegistry);
        Gauge.builder(ServiceMetrics.ANALYSIS_INFLIGHT, backlog, AnalysisBacklog::inFlight)
                .description("이 인스턴스가 붙들고 있는 전달 건수 - 태스크별 몫 (KAN-38)")
                .register(meterRegistry);
        this.duration = ServiceMetrics.latencyTimer(ServiceMetrics.ANALYSIS_DURATION,
                        "전달 접수부터 종결까지 - NFR-PF-01(3초)의 측정값 (KAN-38)")
                .register(meterRegistry);
        ServiceMetrics.registerPercentiles(duration, meterRegistry);
        this.stuckTimeouts = timeoutCounter(meterRegistry, "stuck");
        this.lostTimeouts = timeoutCounter(meterRegistry, "lost");
        this.congestedPolls = pollCounter(meterRegistry, "true");
        this.normalPolls = pollCounter(meterRegistry, "false");
        this.unscorableJudged = judgedCounter(meterRegistry, "unscorable");
        this.misreadJudged = judgedCounter(meterRegistry, "misread");
        this.otherJudged = judgedCounter(meterRegistry, "other");
    }

    private static Counter judgedCounter(MeterRegistry registry, String reason) {
        return Counter.builder(ServiceMetrics.ANALYSIS_JUDGED)
                .description("AI가 판정 실패로 답한 건수 - unscorable은 채점 불가, misread는 다른 발화 (KAN-272)")
                .tag("reason", reason)
                .register(registry);
    }

    private static Counter timeoutCounter(MeterRegistry registry, String reason) {
        return Counter.builder(ServiceMetrics.ANALYSIS_TIMEOUTS)
                .description("타임아웃으로 종결된 작업 수 - stuck은 실행 잔류, lost는 큐 유실 (KAN-38)")
                .tag("reason", reason)
                .register(registry);
    }

    private static Counter pollCounter(MeterRegistry registry, String congested) {
        return Counter.builder(ServiceMetrics.ANALYSIS_POLL_DECISIONS)
                .description("폴링 간격 산출 횟수 - 혼잡 발동 비율의 분자와 분모 (KAN-38)")
                .tag("congested", congested)
                .register(registry);
    }

    /**
     * 분석 1건이 종결됐다 - 성공만 센다.
     * <p>
     * 실패와 타임아웃을 섞으면 지연 분포가 "AI가 답하는 데 걸리는 시간"이 아니라 "포기하는 데
     * 걸리는 시간"과 뒤섞여, NFR-PF-01 준수 여부를 이 값으로 판단할 수 없게 된다. 실패 쪽은
     * 오류율({@code accentury.http.errors})과 타임아웃 카운터가 따로 말한다.
     */
    public void recordCompleted(long elapsedNanos) {
        duration.record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * AI가 계약대로 판정 실패를 돌려줬다 (KAN-272) - 사유별로 센다.
     * <p>
     * 채점 불가({@code ANALYSIS_UNSCORABLE})를 따로 세는 이유는 그것이 사용자의 발화가 아니라
     * 모델 쪽 결함의 신호이기 때문이다. 2026-10-05에는 이 경우가 500으로 나가 로그를 세어 보기 전까지
     * 건수를 알 수 없었다. 모르는 코드는 {@code other}로 접는다 - 태그 값이 열리지 않게.
     */
    public void recordJudged(String errorCode) {
        if (ErrorCode.ANALYSIS_UNSCORABLE.name().equals(errorCode)) {
            unscorableJudged.increment();
        } else if (ErrorCode.ANALYSIS_MISREAD.name().equals(errorCode)) {
            misreadJudged.increment();
        } else {
            otherJudged.increment();
        }
    }

    /** 타임아웃 스위퍼 1회분 - 0건이면 아무것도 세지 않는다. */
    public void recordTimeouts(int stuck, int lost) {
        if (stuck > 0) {
            stuckTimeouts.increment(stuck);
        }
        if (lost > 0) {
            lostTimeouts.increment(lost);
        }
    }

    /** 폴링 간격 산출 1회 - 혼잡 판정이 켜졌는지로 가른다 ({@link PollIntervals}). */
    public void recordPollDecision(boolean congested) {
        (congested ? congestedPolls : normalPolls).increment();
    }
}
