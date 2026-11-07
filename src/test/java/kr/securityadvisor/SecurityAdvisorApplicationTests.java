package kr.securityadvisor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 애플리케이션 전체가 실제 DB 와 함께 뜨는지(설정·마이그레이션·엔티티 매핑) 확인한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecurityAdvisorApplicationTests {

    @Test
    void contextLoads() {}
}
