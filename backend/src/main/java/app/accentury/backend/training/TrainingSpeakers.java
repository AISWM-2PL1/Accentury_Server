package app.accentury.backend.training;

import org.jspecify.annotations.Nullable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * 누구의 샘플을 남기고 어떤 이름으로 남길지 (KAN-239).
 * <p>
 * <b>대상</b>은 학습 활용에 동의한 테스터 계정뿐이다. staging은 인터넷에 공개돼 있어 도메인을 아는 누구나
 * 응시할 수 있으므로, 버킷이 설정됐다는 것만으로는 수집 대상이 한정되지 않는다. 세션 소유 계정이 목록에
 * 있을 때만 저장하고, 익명 세션(웹)과 목록 밖 계정은 저장하지 않는다. 목록이 비면 아무것도 남지 않는다.
 * <p>
 * <b>이름</b>은 세션 ID 원문 대신 {@code HMAC-SHA256(sessionId, 학습 전용 키)}의 hex다(speaker). 같은 세션의 문항은
 * 같은 이름으로 묶이지만, 키를 모르면 {@code test_session}(계정 귀속)이나 로그의 세션 ID와 이어 붙일 수 없다. 분석
 * 작업 ID도 같은 키로 가명(sampleId)이 된다 - 작업 ID 원문은 {@code analysis_job.session_id}로 세션에 곧장 조인되므로
 * 세션만 가려서는 끊기지 않는다 (Codex astra 리뷰 P2). 키는 backend만 읽는 SSM SecureString이다.
 */
public final class TrainingSpeakers {

    private static final String ALGORITHM = "HmacSHA256";

    private final Set<UUID> testerIds;
    private final SecretKeySpec key;

    /**
     * @param testerIds    동의한 테스터의 {@code app_user.id} - 비어 있으면 아무도 대상이 아니다.
     * @param pseudonymKey 학습 전용 HMAC 키 - 32자 이상이어야 한다 ({@code TrainingConfig}가 기동 시 검사한다).
     */
    public TrainingSpeakers(Set<UUID> testerIds, String pseudonymKey) {
        this.testerIds = Set.copyOf(testerIds);
        this.key = new SecretKeySpec(pseudonymKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 이 계정의 세션이 수집 대상인가 - 익명 세션(null)은 언제나 아니다. */
    public boolean consented(@Nullable UUID ownerId) {
        return ownerId != null && testerIds.contains(ownerId);
    }

    /** 세션 ID의 가명 - 객체 키의 세 번째 조각이자 메타의 {@code speaker}다. */
    public String speaker(String sessionId) {
        return hmacHex(sessionId);
    }

    /**
     * 분석 작업 ID의 가명 - 객체 키의 마지막 조각이자 메타의 {@code sampleId}다. 세션 ID와 작업 ID는 접두가 달라
     * ({@code s_}, {@code a_}) 한 키로 가명을 만들어도 두 공간이 겹치지 않는다.
     */
    public String sampleId(String analysisJobId) {
        return hmacHex(analysisJobId);
    }

    private String hmacHex(String value) {
        try {
            // Mac은 스레드 안전하지 않아 호출마다 만든다 - 분석 워커 몇 개가 동시에 부르고, 생성 비용은 무시할 만하다.
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // HmacSHA256은 모든 JDK가 갖춰야 하는 알고리즘이라 여기 오면 런타임이 망가진 것이다.
            throw new IllegalStateException("HmacSHA256을 쓸 수 없다", e);
        }
    }

    int testerCount() {
        return testerIds.size();
    }
}
