package kr.securityadvisor.auth.service;

import java.time.Clock;
import java.time.Instant;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.LoginRequest;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.MemberResponse.LoginResult;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.error.TooManyRequestsException;
import kr.securityadvisor.common.error.UnauthorizedException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 로그인(F-11). 아이디·비밀번호를 대조해 세션에 넣을 {@link MemberPrincipal} 을 만들고,
 * 연속 실패는 {@link LoginAttemptRecorder} 로 기록해 상한에 이르면 잠근다.
 */
@Service
public class AuthService {

    private static final String LOGIN_FAILED = "아이디 또는 비밀번호가 올바르지 않습니다";
    private static final String LOGIN_LOCKED = "로그인 실패가 반복되어 잠시 후 다시 시도할 수 있습니다";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRecorder loginAttemptRecorder;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptRecorder loginAttemptRecorder,
            AuditRecorder auditRecorder,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptRecorder = loginAttemptRecorder;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    /**
     * 자격 증명을 검증한다. 아이디 존재 여부와 비밀번호 오류를 구분해 알려 주지 않는다(계정 열거 방지).
     * 트랜잭션을 열지 않는다 — 실패 기록은 {@link LoginAttemptRecorder} 가 독립 트랜잭션으로 남긴다.
     *
     * @throws UnauthorizedException 계정이 없거나, 비활성이거나, 비밀번호가 틀릴 때
     * @throws TooManyRequestsException 연속 실패로 잠긴 계정일 때(이번 실패로 잠긴 경우 포함)
     */
    public LoginResult login(LoginRequest request) {
        Instant now = clock.instant();
        Member member = memberRepository
                .findById(request.username())
                .filter(Member::canLogin)
                .orElseThrow(() -> {
                    audit(request.username(), null, AuditAction.LOGIN_FAILED, "계정 없음 또는 비활성");
                    return new UnauthorizedException(LOGIN_FAILED);
                });
        if (member.isLocked(now)) {
            audit(member.getUsername(), member.getRole(), AuditAction.LOGIN_LOCKED, "잠금 중 시도");
            throw new TooManyRequestsException(LOGIN_LOCKED);
        }
        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            boolean lockedNow = loginAttemptRecorder.recordFailure(member.getUsername(), now);
            if (lockedNow) {
                audit(member.getUsername(), member.getRole(), AuditAction.LOGIN_LOCKED, "연속 실패로 잠금");
                throw new TooManyRequestsException(LOGIN_LOCKED);
            }
            audit(member.getUsername(), member.getRole(), AuditAction.LOGIN_FAILED, "비밀번호 불일치");
            throw new UnauthorizedException(LOGIN_FAILED);
        }
        loginAttemptRecorder.recordSuccess(member.getUsername());
        audit(member.getUsername(), member.getRole(), AuditAction.LOGIN_SUCCEEDED, null);
        return new LoginResult(toPrincipal(member), MemberResponse.from(member));
    }

    /**
     * 세션의 사용자 아이디로 현재 사용자 정보를 다시 읽는다. 세션 생성 뒤 비활성화된 계정은 401 로 처리한다.
     *
     * @throws UnauthorizedException 계정이 없거나 비활성일 때
     */
    @Transactional(readOnly = true)
    public MemberResponse getCurrentMember(MemberPrincipal principal) {
        return memberRepository
                .findById(principal.username())
                .filter(Member::canLogin)
                .map(MemberResponse::from)
                .orElseThrow(() -> new UnauthorizedException("로그인이 필요하거나 세션이 만료되었습니다"));
    }

    /** 로그인 시도 기록(F-14). 비밀번호는 절대 담지 않는다. */
    private void audit(String username, MemberRole role, AuditAction action, String summary) {
        auditRecorder.record(username, role, AuditTargetType.MEMBER, username, null, action, null, summary);
    }

    private static MemberPrincipal toPrincipal(Member member) {
        return new MemberPrincipal(member.getUsername(), member.getDisplayName(), member.getRole());
    }
}
