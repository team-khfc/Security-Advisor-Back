package kr.securityadvisor.auth.domain;

import java.time.Duration;

/**
 * 로그인 실패 제한 정책(SECURE_CODING.md 2절 "반복된 인증시도 제한"). 설정값에서 만들어 도메인에 넘긴다.
 *
 * @param maxFailedLogins 이 횟수 연속 실패하면 잠근다
 * @param lockDuration 잠금 유지 시간
 */
public record LoginLockPolicy(int maxFailedLogins, Duration lockDuration) {

    public LoginLockPolicy {
        if (maxFailedLogins < 1) {
            throw new IllegalArgumentException("maxFailedLogins 는 1 이상이어야 합니다");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration 은 양수여야 합니다");
        }
    }
}
