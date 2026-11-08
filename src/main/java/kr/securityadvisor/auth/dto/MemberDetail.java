package kr.securityadvisor.auth.dto;

import java.time.Instant;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.domain.SignupSource;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 관리자 계정 목록 한 줄. 비밀번호 해시·실패 횟수는 내보내지 않는다.
 *
 * @param locked 조회 시각 기준 로그인 잠금 여부
 */
public record MemberDetail(
        String username,
        String displayName,
        String department,
        MemberRole role,
        SignupSource signupSource,
        boolean active,
        boolean locked,
        Instant createdAt) {

    public static MemberDetail from(Member member, Instant now) {
        return new MemberDetail(
                member.getUsername(),
                member.getDisplayName(),
                member.getDepartment(),
                member.getRole(),
                member.getSignupSource(),
                member.isActive(),
                member.isLocked(now),
                member.getCreatedAt());
    }
}
