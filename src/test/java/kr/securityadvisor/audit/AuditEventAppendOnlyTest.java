package kr.securityadvisor.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.repository.AuditEventRepository;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 감사 이력은 추가 전용이다(불변식 24) — DB 트리거가 UPDATE·DELETE 를 거부한다. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class AuditEventAppendOnlyTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("저장은 되지만 UPDATE·DELETE 는 트리거가 막는다")
    void appendOnly() {
        AuditEvent event = auditEventRepository.saveAndFlush(AuditEvent.of(
                Instant.now(),
                "admin",
                MemberRole.ADMIN,
                AuditTargetType.MEMBER,
                "applicant",
                null,
                AuditAction.MEMBER_REGISTERED,
                null,
                "테스트",
                "req-1"));
        assertThat(event.getId()).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE audit_events SET summary = '변조' WHERE audit_event_id = ?", event.getId()))
                .hasMessageContaining("추가 전용");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_events WHERE audit_event_id = ?", event.getId()))
                .hasMessageContaining("추가 전용");
    }
}
