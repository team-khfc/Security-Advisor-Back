package kr.securityadvisor.auth.service;

import java.time.Clock;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.MemberDetail;
import kr.securityadvisor.auth.dto.RoleChangeCommand;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 관리자의 계정 관리(F-11): 계정 목록 조회·역할 부여. 역할 상승은 이 경로로만 가능하다(불변식 32).
 *
 * <p>담당자 역할을 잃은 사람의 분야별 담당자 목록 등록은 assignment 컨텍스트가 따로 관리한다 — 이 서비스는 계정 역할만 바꾸고,
 * 자동 배정은 배정 시점에 활성 계정·역할을 다시 확인한다.
 */
@Service
@RequiredArgsConstructor
public class MemberAdminService {

    private final MemberRepository memberRepository;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    /** 계정 목록(아이디순). role 을 주면 그 역할만. */
    @Transactional(readOnly = true)
    public List<MemberDetail> list(MemberPrincipal admin, MemberRole role) {
        admin.requireAnyRole(MemberRole.ADMIN);
        List<Member> members = role == null
                ? memberRepository.findAllByOrderByUsernameAsc()
                : memberRepository.findByRoleOrderByUsernameAsc(role);
        return members.stream()
                .map(member -> MemberDetail.from(member, clock.instant()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberDetail get(MemberPrincipal admin, String username) {
        admin.requireAnyRole(MemberRole.ADMIN);
        return MemberDetail.from(requireMember(username), clock.instant());
    }

    /**
     * 역할 부여·변경. 멱등: 같은 역할이면 바꾸지 않고 현재 값을 돌려준다.
     *
     * @throws DomainRuleViolationException 자기 역할 변경, 마지막 활성 관리자 강등(422)
     */
    @Transactional
    public MemberDetail changeRole(MemberPrincipal admin, String username, RoleChangeCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        if (admin.username().equals(username)) {
            throw new DomainRuleViolationException("자기 자신의 역할은 바꿀 수 없습니다 — 다른 기관 관리자가 변경해야 합니다");
        }
        Member member = requireMember(username);
        MemberRole before = member.getRole();
        if (before == MemberRole.ADMIN
                && command.role() != MemberRole.ADMIN
                && member.isActive()
                && memberRepository.countByRoleAndActiveTrue(MemberRole.ADMIN) <= 1) {
            throw new DomainRuleViolationException("마지막 활성 기관 관리자의 역할은 바꿀 수 없습니다");
        }
        if (member.changeRole(command.role())) {
            auditRecorder.record(
                    admin,
                    AuditTargetType.MEMBER,
                    username,
                    null,
                    AuditAction.MEMBER_ROLE_CHANGED,
                    command.reason(),
                    "역할 변경 " + before + " → " + command.role());
        }
        return MemberDetail.from(member, clock.instant());
    }

    private Member requireMember(String username) {
        return memberRepository
                .findById(username)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다: " + username));
    }
}
