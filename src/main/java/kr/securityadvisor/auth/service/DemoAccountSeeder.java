package kr.securityadvisor.auth.service;

import java.util.List;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.security.MemberRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 역할별 시연 계정을 기동 시 만든다(F-11: 신청자 2·INFRA 1·SECURITY 1·관리자 1).
 *
 * <p>비밀번호는 환경변수 {@code DEMO_ACCOUNT_PASSWORD} 에서 받아 bcrypt 로 저장한다. 이미 있는 아이디는 건드리지 않으므로
 * 여러 번 기동해도 안전하다. 비밀번호가 비어 있으면 아무것도 만들지 않고 경고만 남긴다.
 * 계정 이름·표시 이름은 전부 합성 데이터다.
 */
@Component
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    /** 시연 계정 목록. 구동안내서의 역할별 테스트 계정과 같아야 한다. */
    static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
            new DemoAccount("applicant", "김신청", MemberRole.APPLICANT),
            new DemoAccount("applicant2", "박신청", MemberRole.APPLICANT),
            new DemoAccount("security", "이보안", MemberRole.SECURITY),
            new DemoAccount("infra", "박인프라", MemberRole.INFRA),
            new DemoAccount("admin", "최관리", MemberRole.ADMIN));

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoAccountProperties properties;

    public DemoAccountSeeder(
            MemberRepository memberRepository, PasswordEncoder passwordEncoder, DemoAccountProperties properties) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.hasPassword()) {
            log.warn("DEMO_ACCOUNT_PASSWORD 가 비어 있어 시연 계정을 만들지 않습니다 (.env.example 참고)");
            return;
        }
        String passwordHash = passwordEncoder.encode(properties.accountPassword());
        int created = 0;
        for (DemoAccount account : DEMO_ACCOUNTS) {
            if (memberRepository.existsById(account.username())) {
                continue;
            }
            memberRepository.save(Member.seed(account.username(), passwordHash, account.displayName(), account.role()));
            created++;
        }
        log.info("시연 계정 {}건 생성 (전체 {}건)", created, DEMO_ACCOUNTS.size());
    }

    /** 시연 계정 한 줄. */
    record DemoAccount(String username, String displayName, MemberRole role) {}
}
