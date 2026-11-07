package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.auth.domain.LoginLockPolicy;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.domain.SignupSource;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** members 테이블 매핑(마이그레이션 ↔ 엔티티)과 잠금 컬럼이 저장·조회되는지 실제 PostgreSQL 로 확인한다. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("마이그레이션은 시연 계정을 넣지 않는다 (기동 시 DemoAccountSeeder 가 환경변수로 만든다)")
    void noSeedInMigration() {
        assertThat(memberRepository.count()).isZero();
    }

    @Test
    @DisplayName("회원가입 계정이 부서·가입 경로·잠금 컬럼과 함께 저장되고 다시 읽힌다")
    void saveAndReload() {
        Instant now = Instant.parse("2026-11-07T09:00:00Z");
        Member member = Member.signUp("newuser", "{bcrypt}$2b$10$hash", "홍길동", "정보화부");
        member.recordLoginFailure(now, new LoginLockPolicy(1, Duration.ofMinutes(15)));
        memberRepository.saveAndFlush(member);

        Member reloaded = memberRepository.findById("newuser").orElseThrow();

        assertThat(reloaded.getRole()).isEqualTo(MemberRole.APPLICANT);
        assertThat(reloaded.getSignupSource()).isEqualTo(SignupSource.SELF);
        assertThat(reloaded.getDepartment()).isEqualTo("정보화부");
        assertThat(reloaded.getFailedLoginCount()).isEqualTo(1);
        assertThat(reloaded.isLocked(now)).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}
