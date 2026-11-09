package kr.securityadvisor.auth.dto;

import java.time.Instant;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 로그인한 사용자 정보. 화면은 role 을 메뉴 표시에만 쓰고, 권한 판단은 서버가 한다.
 *
 * @param username 로그인 아이디(사용자 식별자)
 * @param displayName 표시 이름
 * @param role 역할
 * @param department 부서(선택)
 * @param email 메일 알림 주소(선택)
 * @param expiresAt 세션 만료 예정 시각(비활동 기준, 요청마다 연장). 로그인·현재 사용자 응답에만 채운다
 */
public record MemberResponse(
        String username, String displayName, MemberRole role, String department, String email, Instant expiresAt) {

    /** 세션 정보 없이(가입 응답 등). */
    public MemberResponse(String username, String displayName, MemberRole role) {
        this(username, displayName, role, null, null, null);
    }

    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getUsername(),
                member.getDisplayName(),
                member.getRole(),
                member.getDepartment(),
                member.getEmail(),
                null);
    }

    public MemberResponse withExpiresAt(Instant expiresAt) {
        return new MemberResponse(username, displayName, role, department, email, expiresAt);
    }

    /** 로그인 결과: 세션에 저장할 principal 과 응답 본문을 함께 돌려준다. */
    public record LoginResult(MemberPrincipal principal, MemberResponse member) {}
}
