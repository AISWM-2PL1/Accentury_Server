package app.accentury.backend.common;

import app.accentury.backend.DatabaseWipeExtension;
import app.accentury.backend.PostgresTestcontainer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ECS에서 Tomcat이 접속 IP를 먼저 바꿔 놓지 않는다 (KAN-244).
 * <p>
 * Spring Boot는 ECS({@code AWS_EXECUTION_ENV})를 감지하면 {@code server.forward-headers-strategy}를
 * {@code NATIVE}로 켜고, Tomcat RemoteIpValve가 XFF로 {@code remoteAddr}를 바꾼다. 그 Valve는 사설 대역만
 * 내부 프록시로 보므로 XFF의 직전 홉인 CloudFront 엣지 공인 IP가 접속 IP가 되고, {@link ClientIps}는 그것을
 * "신뢰 프록시가 아닌 상대"로 보고 그대로 돌려준다 - 헤더({@code CloudFront-Viewer-Address})를 아예 읽지
 * 않는다 (2026-10-03 staging 실측). MockMvc는 Tomcat Valve를 거치지 않아 이 고장을 못 보므로 실제 서버를 띄운다.
 * <p>
 * 테스트 프로필은 127.0.0.1을 신뢰 프록시로 둔다 - 배포의 ALB 자리다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.main.cloud-platform=aws_ecs",
        "accentury.admin.token=" + ForwardHeadersBootTest.TOKEN})
@ActiveProfiles("test")
// IntegrationTest를 상속하지 못하므로 (웹 환경 직접 지정) 테스트 DB 컨테이너와 클래스 단위 격리를 직접 결선한다.
@Import(PostgresTestcontainer.class)
@ExtendWith(DatabaseWipeExtension.class)
class ForwardHeadersBootTest {

    static final String TOKEN = "forward-headers-boot-test-token-0123456789";

    @LocalServerPort
    private int port;

    @Test
    void ECS로_감지돼도_CloudFront_뒤_접속자_IP는_Viewer_Address다() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminAuth.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        int status;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/v0/test-definitions"))
                    .header(AdminAuth.TOKEN_HEADER, "wrong-token-but-long-enough-0123456789")
                    // staging 실측 모양 - CloudFront가 덧붙인 엣지 공인 IP가 XFF의 마지막 홉이다.
                    .header("X-Forwarded-For", "198.51.100.20, 54.182.245.160")
                    .header(ClientIps.VIEWER_ADDRESS, "198.51.100.20:46532")
                    .GET()
                    .build();
            try (HttpClient client = HttpClient.newHttpClient()) {
                status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            }
        } finally {
            logger.detachAppender(appender);
        }

        assertEquals(401, status);
        List<String> warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertEquals(List.of("관리자 인증 실패 ip=198.51.100.20"), warns,
                "엣지 IP(54.182.245.160)가 찍히면 Tomcat이 접속 IP를 XFF로 바꿔 놓은 것이다");
    }
}
