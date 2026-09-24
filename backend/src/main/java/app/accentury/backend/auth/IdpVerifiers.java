package app.accentury.backend.auth;

import app.accentury.backend.common.ApiException;
import app.accentury.backend.common.ErrorCode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * provider별 {@link IdpVerifier}로 보내는 한 자리 (명세서 §3.9). 가짜 IdP 스위치도 여기서 판정한다.
 * <p>
 * <b>가짜 IdP</b>({@code accentury.auth.fake-idp=true}) - 토큰이 {@code fake:<sub>} 모양이면 IdP를 부르지 않고
 * 그 sub로 확인된 것으로 친다. 로컬 풀스택과 FE 개발이 IdP 콘솔 없이 로그인 흐름을 돌리기 위한 것이고(KAN-224),
 * AI의 fake 엔진과 같은 판단이다. 배포 프로파일에서 켜면 기동이 실패한다 ({@code AuthConfig}). 스위치가 꺼져
 * 있으면 {@code fake:} 토큰도 진짜 IdP로 가서 거절된다.
 */
public final class IdpVerifiers {

    static final String FAKE_PREFIX = "fake:";

    /** 가짜 sub 형식 - 로그와 DB에 그대로 남으므로 안전한 문자만. */
    private static final Pattern FAKE_SUBJECT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final Map<Provider, IdpVerifier> byProvider;
    private final boolean fakeIdp;

    public IdpVerifiers(List<IdpVerifier> verifiers, boolean fakeIdp) {
        Map<Provider, IdpVerifier> map = new EnumMap<>(Provider.class);
        for (IdpVerifier verifier : verifiers) {
            if (map.put(verifier.provider(), verifier) != null) {
                throw new IllegalStateException("IdP 검증기가 둘이다: " + verifier.provider());
            }
        }
        for (Provider provider : Provider.values()) {
            if (!map.containsKey(provider)) {
                // 하나를 빠뜨리면 그 IdP 로그인만 조용히 500이 된다 - 기동 시점에 세운다.
                throw new IllegalStateException("IdP 검증기가 없다: " + provider);
            }
        }
        this.byProvider = Map.copyOf(map);
        this.fakeIdp = fakeIdp;
    }

    /**
     * @throws ApiException 401 {@code AUTH_IDP_TOKEN_INVALID} 또는 502 {@code AUTH_IDP_UNAVAILABLE}
     */
    IdpProfile verify(IdpCredential credential) {
        if (fakeIdp && credential.token().startsWith(FAKE_PREFIX)) {
            String subject = credential.token().substring(FAKE_PREFIX.length());
            if (!FAKE_SUBJECT.matcher(subject).matches()) {
                throw new ApiException(ErrorCode.AUTH_IDP_TOKEN_INVALID);
            }
            return IdpProfile.subjectOnly(credential.provider(), subject);
        }
        return byProvider.get(credential.provider()).verify(credential);
    }
}
