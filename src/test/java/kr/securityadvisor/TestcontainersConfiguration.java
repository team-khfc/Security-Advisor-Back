package kr.securityadvisor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL 컨테이너. Docker 가 실행 중이어야 한다.
 *
 * <p>DB 가 필요한 테스트는 {@code @Import(TestcontainersConfiguration.class)} 로 이 설정을 가져온다.
 * 운영과 같은 버전(compose.yaml 의 postgres 이미지)을 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
