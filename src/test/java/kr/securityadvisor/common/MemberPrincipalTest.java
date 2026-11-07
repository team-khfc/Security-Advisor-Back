package kr.securityadvisor.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 프레임워크 없는 단위 테스트: 역할·소유자 검사 규칙. */
class MemberPrincipalTest {

    private final MemberPrincipal applicant = new MemberPrincipal("applicant", "김신청", MemberRole.APPLICANT);

    @Test
    @DisplayName("허용 역할 목록에 없으면 ForbiddenException 으로 거부한다")
    void roleNotAllowed() {
        assertThatThrownBy(() -> applicant.requireAnyRole(MemberRole.SECURITY, MemberRole.INFRA))
                .isInstanceOf(ForbiddenException.class);
        assertThatCode(() -> applicant.requireAnyRole(MemberRole.APPLICANT)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("자기 자원은 허용하고 다른 사용자의 자원은 ForbiddenException 으로 거부한다")
    void ownerCheck() {
        assertThatCode(() -> applicant.requireOwner("applicant")).doesNotThrowAnyException();
        assertThatThrownBy(() -> applicant.requireOwner("security")).isInstanceOf(ForbiddenException.class);
    }
}
