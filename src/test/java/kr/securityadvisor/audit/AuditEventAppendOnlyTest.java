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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 감사 이력은 추가 전용이다(불변식 24) — DB 트리거가 UPDATE·DELETE 를 거부한다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class AuditEventAppendOnlyTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("저장은 되지만 UPDATE·DELETE 는 트리거가 막는다")
    void appendOnly() {
        // @DataJpaTest 는 테스트 전체가 한 트랜잭션이라 (1) 미커밋 행은 다른 트랜잭션에서 안 보이고
        // (2) 트리거 오류 뒤의 문장은 "transaction is aborted" 만 받는다. 그래서 저장·UPDATE·DELETE 를 각각
        // 새 트랜잭션(REQUIRES_NEW)에서 실행한다. 저장된 행은 추가 전용이라 지우지 않는다(테스트 DB 는 컨테이너마다 새로 만든다).
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Long eventId = separate.execute(status -> auditEventRepository
                .saveAndFlush(AuditEvent.of(
                        Instant.now(),
                        "admin",
                        MemberRole.ADMIN,
                        AuditTargetType.MEMBER,
                        "applicant",
                        null,
                        AuditAction.MEMBER_REGISTERED,
                        null,
                        "테스트",
                        "req-1"))
                .getId());
        assertThat(eventId).isNotNull();

        assertThatThrownBy(() -> separate.executeWithoutResult(status -> jdbcTemplate.update(
                        "UPDATE audit_events SET summary = '변조' WHERE audit_event_id = ?", eventId)))
                .hasMessageContaining("추가 전용");
        assertThatThrownBy(() -> separate.executeWithoutResult(
                        status -> jdbcTemplate.update("DELETE FROM audit_events WHERE audit_event_id = ?", eventId)))
                .hasMessageContaining("추가 전용");
    }
}
