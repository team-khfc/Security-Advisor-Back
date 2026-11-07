package kr.securityadvisor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** 시드 계정이 마이그레이션으로 적재되는지 확인한다. */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("시드 계정 4종(역할별)이 username 으로 조회된다")
    void seededMembers() {
        for (MemberRole role : MemberRole.values()) {
            Member member = memberRepository.findById(role.name().toLowerCase()).orElseThrow();
            assertThat(member.getRole()).isEqualTo(role);
            assertThat(member.canLogin()).isTrue();
        }
    }

    @Test
    @DisplayName("시드 비밀번호 해시는 DelegatingPasswordEncoder 형식({bcrypt})이다")
    void passwordHashFormat() {
        Member member = memberRepository.findById("admin").orElseThrow();
        assertThat(member.getPasswordHash()).startsWith("{bcrypt}$2");
    }
}
