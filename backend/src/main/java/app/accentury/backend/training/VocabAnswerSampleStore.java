package app.accentury.backend.training;

import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 단어 답안 정오 기록을 보존하는 곳 (KAN-276). 구현은 예외를 밖으로 내지 않는다 - 답안 저장과 채점은 이미 끝난
 * 뒤라 기록 실패가 응답에 닿으면 안 된다.
 * <p>
 * 버킷이 없는 배포(로컬, 테스트)는 {@link #NONE}이다.
 */
@FunctionalInterface
public interface VocabAnswerSampleStore {

    /** 아무것도 저장하지 않는다 - {@code accentury.training.bucket}이 없는 배포의 자리. */
    VocabAnswerSampleStore NONE = sample -> {
    };

    /** 기록을 시작하는 testVersion (KAN-276) - 이 버전과 그 뒤에 발행한 버전의 세션만 기록한다. */
    String FIRST_RECORDED_VERSION = "gn-2026.10.2";

    /** {@code <방언>-<연>.<월>.<순번>} - 순번이 두 자리가 되면 문자열 비교가 틀리므로 숫자로 나눠 비교한다. */
    Pattern VERSION = Pattern.compile("[a-z]+-(\\d{4})\\.(\\d{2})\\.(\\d+)");

    void save(VocabAnswerSample sample);

    /**
     * 이 testVersion의 세션 답안을 기록하는가 - {@link #FIRST_RECORDED_VERSION}과 같거나 뒤인 버전이다. 형식이 다른
     * 버전(테스트 픽스처의 {@code gn-2026.09.t7} 등)은 기록하지 않는다.
     */
    static boolean recorded(String testVersion) {
        long[] version = parse(testVersion);
        long[] first = parse(FIRST_RECORDED_VERSION);
        if (version == null || first == null) {
            return false;
        }
        for (int i = 0; i < version.length; i++) {
            if (version[i] != first[i]) {
                return version[i] > first[i];
            }
        }
        return true;
    }

    private static long @Nullable [] parse(String testVersion) {
        Matcher matcher = VERSION.matcher(testVersion);
        if (!matcher.matches()) {
            return null;
        }
        return new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)),
                Long.parseLong(matcher.group(3))};
    }
}
