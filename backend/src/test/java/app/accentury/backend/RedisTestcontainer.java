package app.accentury.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 계정 인증 테스트가 쓰는 Redis 컨테이너 (KAN-223) - Refresh 토큰 저장소다.
 * <p>
 * {@link PostgresTestcontainer}와 같은 모양이다: JVM당 하나를 static으로 띄우고, 컨텍스트가 닫혀도 멈추지 않게
 * 소멸 메서드를 비운다. 이미지는 배포(ElastiCache 7.1)와 로컬 compose와 같은 7 메이저다.
 * <p>
 * Refresh를 쓰지 않는 테스트는 이것을 import하지 않는다 - backend는 Redis 연결을 첫 사용 때 맺고 health에서도
 * 뺐으므로(application.yml) Redis 없이 뜬다. 그 자체가 "Redis가 없어도 익명 응시는 산다"(NFR-AV-02)의 일부다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisTestcontainer {

    @SuppressWarnings("resource")    // Ryuk이 JVM 종료 후 정리한다 (PostgresTestcontainer와 같다).
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS;
    }
}
