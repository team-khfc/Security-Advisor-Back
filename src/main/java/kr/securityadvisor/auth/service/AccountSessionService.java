package kr.securityadvisor.auth.service;

import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.security.AccountSessionValidator;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매 요청마다 DB의 계정 상태를 확인한다. 권한 정보는 캐시하지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountSessionService implements AccountSessionValidator {
    private final MemberRepository memberRepository;

    @Override
    public boolean isValid(MemberPrincipal principal) {
        return memberRepository
                .findById(principal.username())
                .filter(member -> member.isActive()
                        && member.getRole() == principal.role()
                        && member.getSessionVersion() == principal.sessionVersion())
                .isPresent();
    }
}
