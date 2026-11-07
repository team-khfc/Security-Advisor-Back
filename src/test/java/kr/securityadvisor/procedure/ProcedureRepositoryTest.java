package kr.securityadvisor.procedure;

import static org.assertj.core.api.Assertions.assertThat;

import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.procedure.domain.Procedure;
import kr.securityadvisor.procedure.repository.ProcedureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 저장소 테스트: 실제 PostgreSQL 에 Flyway 마이그레이션을 적용한 상태에서 쿼리를 확인한다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProcedureRepositoryTest {

    @Autowired
    private ProcedureRepository procedureRepository;

    @Test
    @DisplayName("마이그레이션으로 넣은 기본 절차 3종이 조회된다")
    void seededProcedures() {
        assertThat(procedureRepository.findAllByActiveTrueOrderByIdAsc())
                .extracting(Procedure::getCode)
                .containsExactly("SECURITY_REVIEW", "FIREWALL_REQUEST", "USB_REQUEST");
    }

    @Test
    @DisplayName("비활성화한 절차는 목록에서 빠진다")
    void deactivatedProcedureIsHidden() {
        Procedure usb = procedureRepository.findByCode("USB_REQUEST").orElseThrow();
        usb.deactivate();
        procedureRepository.flush();

        assertThat(procedureRepository.findAllByActiveTrueOrderByIdAsc())
                .extracting(Procedure::getCode)
                .doesNotContain("USB_REQUEST");
    }
}
