package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.domain.SignupSource;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.auth.service.DemoAccountSeeder;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 시연 계정 5건(신청자 2·SECURITY 1·INFRA 1·ADMIN 1)이 환경변수 비밀번호로 만들어지고, 다시 기동해도 중복 생성되지 않는지 확인한다. */
@SpringBootTest(properties = "securityadvisor.demo.account-password=Test-Demo-1234")
@Import(TestcontainersConfiguration.class)
class DemoAccountSeederTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DemoAccountSeeder seeder;

    @Test
    @DisplayName("역할별 시연 계정 5건이 SEED 출처·bcrypt 해시로 만들어진다")
    void seedsFiveAccounts() {
        assertThat(memberRepository.findById("applicant")).isPresent();
        assertThat(memberRepository.findById("applicant2"))
                .get()
                .extracting(Member::getRole)
                .isEqualTo(MemberRole.APPLICANT);
        assertThat(memberRepository.findById("security")).get().extracting(Member::getRole).isEqualTo(MemberRole.SECURITY);
        assertThat(memberRepository.findById("infra")).get().extracting(Member::getRole).isEqualTo(MemberRole.INFRA);
        Member admin = memberRepository.findById("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(MemberRole.ADMIN);
        assertThat(admin.getSignupSource()).isEqualTo(SignupSource.SEED);
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("Test-Demo-1234", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("다시 실행해도 이미 있는 계정은 건드리지 않는다")
    void idempotent() {
        long before = memberRepository.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(memberRepository.count()).isEqualTo(before);
    }
}
