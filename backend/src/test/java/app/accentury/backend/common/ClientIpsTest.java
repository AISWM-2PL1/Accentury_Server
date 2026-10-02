package app.accentury.backend.common;

import app.accentury.backend.PropertiesFixture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 요청 제한 기준 IP의 결정 규칙 (KAN-28, API 명세서 §2.5).
 * <p>
 * {@code X-Forwarded-For}는 클라이언트가 마음대로 쓰는 헤더다 - 검증 없이 읽으면
 * 요청마다 다른 값을 넣어 IP 제한을 통째로 우회할 수 있다. 그래서 "누가 보냈는지"가
 * 판정의 전부다.
 */
class ClientIpsTest {

    @Test
    void 신뢰_목록이_비어_있으면_헤더를_읽지_않는다() {
        // 프록시 없이 뜬 서버의 기본값 - 헤더 위조가 통하면 제한이 무의미하다.
        ClientIps clientIps = clientIps(List.of());

        assertEquals("198.51.100.7",
                clientIps.resolve(request("198.51.100.7", "1.2.3.4")));
    }

    @Test
    void 신뢰하지_않는_상대가_보낸_헤더는_무시한다() {
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("198.51.100.7",
                clientIps.resolve(request("198.51.100.7", "1.2.3.4")));
    }

    @Test
    void 신뢰_프록시_뒤에서는_헤더의_마지막_값을_쓴다() {
        // 프록시는 자기가 본 상대를 오른쪽에 덧붙인다 - 왼쪽일수록 위조 가능 구간이다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("203.0.113.9",
                clientIps.resolve(request("10.1.2.3", "1.2.3.4, 203.0.113.9")));
    }

    @Test
    void 체인_안의_신뢰_프록시는_건너뛴다() {
        // ALB 두 단을 지나면 오른쪽 두 값이 내부 IP다 - 그 앞의 값이 실제 접속자다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("203.0.113.9",
                clientIps.resolve(request("10.1.2.3", "1.2.3.4, 203.0.113.9, 10.0.0.5")));
    }

    @Test
    void 전부_신뢰_프록시면_접속_IP로_돌아간다() {
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("10.1.2.3",
                clientIps.resolve(request("10.1.2.3", "10.0.0.9, 10.0.0.5")));
    }

    @Test
    void 형식이_이상한_값을_만나면_접속_IP로_돌아간다() {
        // 판정 불가를 "제한 없음"으로 해석하지 않는다 - 위조 값으로 키를 흩뿌릴 수 있다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("10.1.2.3",
                clientIps.resolve(request("10.1.2.3", "not-an-ip")));
    }

    @Test
    void 포트와_대괄호를_걷어낸다() {
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("203.0.113.9",
                clientIps.resolve(request("10.1.2.3", "203.0.113.9:51234")));
        assertEquals("2001:db8::1",
                clientIps.resolve(request("10.1.2.3", "[2001:db8::1]:51234")));
    }

    @Test
    void IPv6_대역도_신뢰할_수_있다() {
        ClientIps clientIps = clientIps(List.of("2001:db8::/32"));

        assertEquals("203.0.113.9",
                clientIps.resolve(request("2001:db8::5", "203.0.113.9")));
    }

    @Test
    void 접두_길이가_없으면_그_주소_하나만_신뢰한다() {
        ClientIps clientIps = clientIps(List.of("10.1.2.3"));

        assertEquals("203.0.113.9",
                clientIps.resolve(request("10.1.2.3", "203.0.113.9")));
        assertEquals("10.1.2.4",
                clientIps.resolve(request("10.1.2.4", "203.0.113.9")));
    }

    @Test
    void IPv4와_IPv6은_섞어서_비교하지_않는다() {
        // 32비트 접두를 16바이트 주소에 얹으면 엉뚱한 대역이 신뢰된다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));

        assertEquals("2001:db8::5",
                clientIps.resolve(request("2001:db8::5", "203.0.113.9")));
    }

    // === CloudFront-Viewer-Address (KAN-244) ===

    @Test
    void CloudFront_뒤에서는_Viewer_Address가_XFF보다_먼저다() {
        // 2026-10-02 staging 실측 모양 - XFF의 직전 홉이 CloudFront 오리진 페이싱 공인 IP라 XFF 규칙으로는
        // 엣지 IP(54.182.245.160)가 뽑혔다. 헤더가 실제 접속자다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("10.1.2.3", "58.72.42.92, 54.182.245.160");
        request.addHeader(ClientIps.VIEWER_ADDRESS, "58.72.42.92:46532");

        assertEquals("58.72.42.92", clientIps.resolve(request));
    }

    @Test
    void 신뢰하지_않는_상대가_보낸_Viewer_Address는_무시한다() {
        // CloudFront를 거치지 않고 직접 붙은 상대가 쓴 값은 XFF와 똑같이 믿지 않는다.
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest request = request("198.51.100.7", "1.2.3.4");
        request.addHeader(ClientIps.VIEWER_ADDRESS, "1.2.3.4:443");

        assertEquals("198.51.100.7", clientIps.resolve(request));
        assertEquals("198.51.100.7", clientIps(List.of()).resolve(request));
    }

    @Test
    void Viewer_Address가_없거나_이상하면_XFF_규칙으로_내려간다() {
        ClientIps clientIps = clientIps(List.of("10.0.0.0/8"));
        MockHttpServletRequest missing = request("10.1.2.3", "1.2.3.4, 203.0.113.9");
        MockHttpServletRequest malformed = request("10.1.2.3", "1.2.3.4, 203.0.113.9");
        malformed.addHeader(ClientIps.VIEWER_ADDRESS, "not-an-ip:443");

        assertEquals("203.0.113.9", clientIps.resolve(missing));
        assertEquals("203.0.113.9", clientIps.resolve(malformed));
    }

    @Test
    void Viewer_Address의_포트와_IPv6_표기를_걷어낸다() {
        assertEquals("198.51.100.10", ClientIps.viewerAddress("198.51.100.10:46532"));
        assertEquals("2001:db8::1", ClientIps.viewerAddress("[2001:db8::1]:46532"));
        // 대괄호 없는 IPv6 - 마지막 콜론 뒤가 포트다.
        assertEquals("2001:db8::1", ClientIps.viewerAddress("2001:db8::1:46532"));
        // 포트 없이 와도 리터럴이면 받는다.
        assertEquals("198.51.100.10", ClientIps.viewerAddress("198.51.100.10"));
        assertEquals("2001:db8::1", ClientIps.viewerAddress("2001:db8::1"));
        assertNull(ClientIps.viewerAddress(""));
        assertNull(ClientIps.viewerAddress("[2001:db8::1"));
        assertNull(ClientIps.viewerAddress("example.com:443"));
    }

    @Test
    void 설정값이_IP나_CIDR이_아니면_기동에_실패한다() {
        // 조용히 "신뢰 안 함"으로 흘리면 ALB 배포에서 전원이 한 키를 공유한다.
        assertThrows(IllegalArgumentException.class, () -> clientIps(List.of("proxy.internal")));
        assertThrows(IllegalArgumentException.class, () -> clientIps(List.of("10.0.0.0/64")));
        assertThrows(IllegalArgumentException.class, () -> clientIps(List.of("10.0.0.0/x")));
    }

    private static ClientIps clientIps(List<String> trustedProxies) {
        return new ClientIps(PropertiesFixture.withTrustedProxies(trustedProxies));
    }

    private static MockHttpServletRequest request(String remoteAddr, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
