package kr.securityadvisor.auth.service;

import java.time.Clock;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.ActivationCommand;
import kr.securityadvisor.auth.dto.ContactChangeCommand;
import kr.securityadvisor.auth.dto.MemberDetail;
import kr.securityadvisor.auth.dto.RoleAssignmentCommand;
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

    /**
     * 활성/비활성 설정(관리자). 멱등. 자기 자신·마지막 활성 관리자 비활성화는 422.
     * 비활성 계정은 로그인·자동 배정에서 빠지고, 기존 배정은 유지된 채 "재배정 필요"로 다룬다(담당자 목록은 assignment 가 관리).
     */
    @Transactional
    public MemberDetail setActivation(MemberPrincipal admin, String username, ActivationCommand command) {
        admin.requireAnyRole(MemberRole.ADMIN);
        Member member = requireMember(username);
        if (member.isActive() == command.active()) {
            return MemberDetail.from(member, clock.instant());
        }
        if (!command.active()) {
            if (admin.username().equals(username)) {
                throw new DomainRuleViolationException("자기 자신의 계정은 비활성화할 수 없습니다");
            }
            if (member.getRole() == MemberRole.ADMIN
                    && memberRepository.countByRoleAndActiveTrue(MemberRole.ADMIN) <= 1) {
                throw new DomainRuleViolationException("마지막 활성 기관 관리자는 비활성화할 수 없습니다");
            }
            member.deactivate();
        } else {
            member.activate();
        }
        auditRecorder.record(
                admin,
                AuditTargetType.MEMBER,
                username,
                null,
                AuditAction.MEMBER_ACTIVATION_CHANGED,
                command.reason(),
                "계정 " + (command.active() ? "활성화" : "비활성화"));
        return MemberDetail.from(member, clock.instant());
    }

    /** 역할과 활성 상태를 한 번에(관리자 화면의 "역할 지정" 명령). 각 규칙은 개별 메서드와 같다. */
    @Transactional
    public MemberDetail assignRole(MemberPrincipal admin, String username, RoleAssignmentCommand command) {
        MemberDetail detail = changeRole(admin, username, new RoleChangeCommand(command.role(), command.reason()));
        if (command.active() != null) {
            detail = setActivation(admin, username, new ActivationCommand(command.active(), command.reason()));
        }
        return detail;
    }

    /** 연락처 변경 — 본인 또는 기관 관리자. 값은 감사 요약에 넣지 않는다(개인정보). */
    @Transactional
    public MemberDetail changeContact(MemberPrincipal actor, String username, ContactChangeCommand command) {
        if (!actor.username().equals(username)) {
            actor.requireAnyRole(MemberRole.ADMIN);
        }
        Member member = requireMember(username);
        boolean had = member.getEmail() != null;
        member.changeEmail(command.email());
        auditRecorder.record(
                actor,
                AuditTargetType.MEMBER,
                username,
                null,
                AuditAction.MEMBER_CONTACT_CHANGED,
                null,
                "메일 알림 주소 " + (member.getEmail() == null ? "해제" : (had ? "변경" : "등록")));
        return MemberDetail.from(member, clock.instant());
    }

    private Member requireMember(String username) {
        return memberRepository
                .findById(username)
                .orElseThrow(() -> new NotFoundException("계정을 찾을 수 없습니다: " + username));
    }
}
