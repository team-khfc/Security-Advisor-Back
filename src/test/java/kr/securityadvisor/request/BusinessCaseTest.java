package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.request.domain.BusinessCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사업 건 애그리거트의 생성·소유·revision 규칙. */
class BusinessCaseTest {

    @Test
    @DisplayName("사업명은 필수이며 앞뒤 공백을 제거한다")
    void titleRequired() {
        assertThatThrownBy(() -> BusinessCase.create("applicant", " ", null, null, 1L))
                .isInstanceOf(DomainRuleViolationException.class);

        BusinessCase businessCase = BusinessCase.create("applicant", " 민원 포털 ", "개요", "정보화부", 1L);

        assertThat(businessCase.getTitle()).isEqualTo("민원 포털");
        assertThat(businessCase.getPublicationId()).isEqualTo(1L);
        assertThat(businessCase.isOwnedBy("applicant")).isTrue();
        assertThat(businessCase.isOwnedBy("applicant2")).isFalse();
    }

    @Test
    @DisplayName("오래된 revision 으로 수정하면 ConflictException")
    void staleRevision() {
        BusinessCase businessCase = BusinessCase.create("applicant", "민원 포털", null, null, 1L);

        assertThatThrownBy(() -> businessCase.requireRevision(3)).isInstanceOf(ConflictException.class);
    }
}
