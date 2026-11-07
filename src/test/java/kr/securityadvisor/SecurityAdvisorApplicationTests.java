package kr.securityadvisor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 애플리케이션 전체가 실제 DB 와 함께 뜨는지(설정·마이그레이션·엔티티 매핑) 확인한다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecurityAdvisorApplicationTests {

    @Test
    void contextLoads() {}
}
