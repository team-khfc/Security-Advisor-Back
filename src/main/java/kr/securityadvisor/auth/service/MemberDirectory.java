package kr.securityadvisor.auth.service;

import java.util.List;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.security.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 컨텍스트(assignment 등)가 계정의 존재·역할을 확인할 때 쓰는 조회 전용 창구. Member 엔티티를 밖으로 내보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MemberDirectory {

    private final MemberRepository memberRepository;

    /**
     * 활성 계정이고 요구 역할인지 확인한다.
     *
     * @throws DomainRuleViolationException 없는 계정·비활성·역할 불일치(422)
     */
    @Transactional(readOnly = true)
    public void requireActiveWithRole(String username, MemberRole role) {
        var member = memberRepository
                .findById(username)
                .orElseThrow(() -> new DomainRuleViolationException("계정이 없습니다: " + username));
        if (!member.isActive()) {
            throw new DomainRuleViolationException("비활성 계정입니다: " + username);
        }
        if (member.getRole() != role) {
            throw new DomainRuleViolationException(
                    username + " 의 역할은 " + member.getRole() + " 이라 " + role + " 담당자로 등록할 수 없습니다");
        }
    }

    /** 그 역할의 활성 계정 아이디 목록(예: 관리자 알림 수신자). */
    @Transactional(readOnly = true)
    public List<String> activeUsernamesWithRole(MemberRole role) {
        return memberRepository.findByRoleOrderByUsernameAsc(role).stream()
                .filter(member -> member.isActive())
                .map(member -> member.getUsername())
                .toList();
    }

    /** 활성 계정이고 역할이 맞으면 true(자동 배정이 담당자 목록의 사람을 다시 확인할 때). */
    @Transactional(readOnly = true)
    public boolean isActiveWithRole(String username, MemberRole role) {
        return memberRepository
                .findById(username)
                .filter(member -> member.isActive() && member.getRole() == role)
                .isPresent();
    }
}
