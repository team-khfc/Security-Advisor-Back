package kr.securityadvisor.auth.service;

import lombok.RequiredArgsConstructor;
import java.time.Instant;
import kr.securityadvisor.auth.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 성공·실패를 계정에 기록한다.
 *
 * <p>{@link AuthService#login} 은 실패 시 예외를 던지므로, 실패 기록이 같은 트랜잭션에서 롤백되지 않도록
 * 별도 빈의 독립 트랜잭션으로 저장한다.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptRecorder {

    private final MemberRepository memberRepository;
    private final LoginPolicyProperties properties;

    /**
     * 비밀번호 오류를 기록한다.
     *
     * @return 이번 실패로 계정이 잠겼으면 true
     */
    @Transactional
    public boolean recordFailure(String username, Instant now) {
        return memberRepository
                .findById(username)
                .map(member -> member.recordLoginFailure(now, properties.toPolicy()))
                .orElse(false);
    }

    /** 로그인 성공: 실패 횟수·잠금을 초기화한다. */
    @Transactional
    public void recordSuccess(String username) {
        memberRepository.findById(username).ifPresent(member -> member.recordLoginSuccess());
    }
}
