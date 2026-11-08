package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.securityadvisor.llm.service.InternalInfoGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 모델 출력의 IP·대역 토큰은 서버가 넘긴 사실 값에 있을 때만 통과한다. */
class InternalInfoGuardTest {

    private final InternalInfoGuard guard = new InternalInfoGuard();

    @Test
    @DisplayName("사실로 넘기지 않은 IP 는 근거 없는 내부 정보로 잡는다")
    void unbackedIp() {
        assertThat(guard.findUnbacked("출발지는 10.0.0.0/24 이고 목적지는 10.0.1.10 입니다", List.of("10.0.1.10")))
                .containsExactly("10.0.0.0/24");
        assertThat(guard.findUnbacked("출발지는 10.0.0.0/24 입니다", List.of("10.0.0.0/16 (합성)")))
                .containsExactly("10.0.0.0/24");
        assertThat(guard.findUnbacked("HTTPS 443 포트, 기간 2026-12-01~2027-11-30", List.of()))
                .isEmpty();
        assertThat(guard.findUnbacked(null, List.of())).isEmpty();
    }
}
