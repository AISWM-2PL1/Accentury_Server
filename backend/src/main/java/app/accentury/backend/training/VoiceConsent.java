package app.accentury.backend.training;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * 한 세션에 유효한 음성 저장 동의 (KAN-269) - 이 값이 있는 분석 요청만 학습 샘플로 남는다.
 * <p>
 * 웹 익명 세션은 세션 생성 때 기록한 동의이고, 계정 세션은 업로드 시점의 계정 동의다 ({@code VoiceConsents}).
 * 버전과 시각은 라벨 JSON에 함께 적는다 - 익명 세션의 동의 기록은 세션 행과 함께 만료 삭제되므로, 음성 옆에
 * 남기지 않으면 동의 증빙이 사라진다.
 *
 * @param version     동의한 음성 저장 동의 버전
 * @param consentedAt 동의 시각
 * @param ownerId     세션 소유 계정 - 익명 세션은 null이다. 계정과 음성의 대응표({@link TrainingVoiceOwners})에만
 *                    쓰고 객체 키와 라벨에는 싣지 않는다.
 */
public record VoiceConsent(String version, Instant consentedAt, @Nullable UUID ownerId) {

    /** 계정 id는 찍지 않는다 - 로그 한 줄이 세션과 계정의 대응표가 되면 안 된다 (KAN-240). */
    @Override
    public String toString() {
        return "VoiceConsent[version=" + version + ", account=" + (ownerId != null) + "]";
    }
}
