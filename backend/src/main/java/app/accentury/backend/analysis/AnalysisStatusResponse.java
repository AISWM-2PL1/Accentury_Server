package app.accentury.backend.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * {@code GET .../analyses}의 200 응답 - 전체 음성 문항 상태 일괄 조회 (API 명세서 §3.4).
 * <p>
 * 세션 버전의 음성 문항 5개가 seq 순서로 전부 실린다 - 시도가 없는 문항은
 * {@code NOT_SUBMITTED}다. <b>점수는 절대 싣지 않는다</b> (KAN-12 - 문항 중간 점수 미노출).
 *
 * @param pollAfterMs 다음 조회까지 기다릴 시간(ms) - 서버가 통제하고 혼잡 시 올린다 (§5.3).
 * @param queue       혼잡 안내 (KAN-272) - 혼잡 판정이 켜져 있고 이 세션에 분석 중인 시도가 있을 때만
 *                    실린다. 그 밖에는 필드 자체가 없다 (기존 클라이언트는 영향이 없다).
 * @param items       음성 문항별 대표 상태 - seq 오름차순 고정
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisStatusResponse(long pollAfterMs, @Nullable Queue queue, List<Item> items) {

    /**
     * 혼잡할 때의 대기 안내 (§3.4, KAN-272). 클라이언트는 이 값으로 "앞에 N건이 있어요"를 보여 주고
     * 자동 폴링 상한을 60초에서 300초로 늘린다 (§5.3 규칙 5).
     * <p>
     * 예상 시간이 아니라 건수를 준다 (2026-10-06 결정). 서버는 AI가 지금 몇 대인지 모르므로 시간으로
     * 바꾸면 1대 기준의 어림이 되고, 2대나 3대일 때 실제보다 두세 배 길게 나온다. 건수는 사실이다.
     *
     * @param ahead 이 세션의 가장 오래된 분석 중 시도보다 먼저 접수된 전 세션의 분석 중 시도 수.
     *              0이면 이 세션이 맨 앞이다. 이 세션 자신의 시도는 세지 않는다.
     */
    public record Queue(long ahead) {
    }

    /** 문항 단위 상태 - 작업 상태({@link AnalysisJobStatus})에 "시도 없음"을 더한 표현이다 (§3.4). */
    public enum Status {
        NOT_SUBMITTED,
        PROCESSING,
        COMPLETED,
        RETRYABLE_FAILED,
        FAILED;

        static Status from(AnalysisJobStatus status) {
            return valueOf(status.name());
        }
    }

    /**
     * 문항 하나의 대표 상태. 시도가 여럿이면 이렇게 접는다 (2026-08-10 확정, 2026-08-13 보강):
     * 최신 시도부터 거슬러 올라가 처음 만나는 분석 중(PROCESSING) 또는 성공(COMPLETED)이
     * 대표이고 실패는 건너뛴다 - 채점 대상을 갈아치울 수 있는 새 시도가 돌고 있으면
     * 대기(새 결과 반영), 아니면 살아 있는 최신 성공이 대표(재녹음 불필요)다.
     * 전부 실패면 최신 시도의 실패 상태다. /complete의 "최신 성공 시도 1건이면 완료"
     * 규칙(§3.6, §5.1)과 어긋나지 않게 하기 위한 규칙이다.
     *
     * @param quality COMPLETED일 때만 - AI 품질 판정 코드 (예: OK)
     * @param error   실패 상태일 때만 - 사유 코드와 재녹음 유효 여부
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String itemId, Status status, @Nullable String quality, @Nullable Error error) {

        /**
         * @param retryable 재녹음(새 시도)이 도움이 되는가 - RETRYABLE_FAILED면 true다.
         *                  시도 상한(KAN-28, §2.5)은 별개로 적용된다.
         */
        public record Error(String code, boolean retryable) {
        }

        static Item notSubmitted(String itemId) {
            return new Item(itemId, Status.NOT_SUBMITTED, null, null);
        }

        static Item from(AnalysisJob job) {
            return switch (job.status()) {
                case PROCESSING -> new Item(job.itemId(), Status.PROCESSING, null, null);
                case COMPLETED -> new Item(job.itemId(), Status.COMPLETED, job.qualityCode(), null);
                case RETRYABLE_FAILED, FAILED -> new Item(job.itemId(), Status.from(job.status()), null,
                        new Error(job.errorCode() != null ? job.errorCode() : "INTERNAL_ERROR",
                                job.status() == AnalysisJobStatus.RETRYABLE_FAILED));
            };
        }
    }
}
