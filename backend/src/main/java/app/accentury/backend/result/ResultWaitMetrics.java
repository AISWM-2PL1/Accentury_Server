package app.accentury.backend.result;

import app.accentury.backend.observability.ServiceMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 결과 화면 대기 시간 지표 (KAN-284, {@link ServiceMetrics#RESULT_WAIT}).
 * <p>
 * 시작은 세션의 첫 {@code /complete} 호출이고({@code test_session.complete_requested_at}), 끝은 READY를 돌려준 호출이다.
 * 대기 화면이 {@code pollAfterMs}마다 다시 부르므로 값에 폴링 간격이 들어간다 - 서버가 준비된 시각이 아니라 사용자가
 * 기다린 시간을 재는 것이 의도다. 혼잡 시 간격이 최대 60초까지 늘어나므로(KAN-272) 그만큼 길게 나온다.
 * <p>
 * 태그는 없다. 만료로 끊긴 대기는 세지 않는다 - {@code analysis.duration}이 성공 건만 세는 것과 같은 이유로, 끝나지
 * 않은 대기를 섞으면 "결과를 받기까지 얼마나 걸리나"를 이 값으로 읽을 수 없다.
 */
@Component
public class ResultWaitMetrics {

    private final Timer wait;

    ResultWaitMetrics(MeterRegistry meterRegistry) {
        this.wait = ServiceMetrics.latencyTimer(ServiceMetrics.RESULT_WAIT,
                        "첫 /complete부터 READY까지 - 결과 화면 체감 대기 (KAN-284)")
                .register(meterRegistry);
        ServiceMetrics.registerPercentiles(wait, meterRegistry);
    }

    /** 세션 1건의 대기가 READY로 끝났다 - 세션당 한 번만 부른다. */
    void recordReady(Duration waited) {
        wait.record(waited);
    }
}
