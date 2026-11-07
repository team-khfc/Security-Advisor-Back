package kr.securityadvisor.request.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.request.domain.SecurityRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 사업 건 진행 상태는 저장하지 않고 하위 신청에서 계산한다(불변식 15). */
class ProgressSummaryTest {

    @Test
    @DisplayName("상태별 건수를 '2건 중 1건 협의 중, 1건 철회' 형태로 요약한다")
    void summarize() {
        SecurityRequest firewall = SecurityRequest.create(1L, DocTypeCode.of("FIREWALL"));
        firewall.requestReview();
        SecurityRequest review = SecurityRequest.create(1L, DocTypeCode.of("SECURITY_REVIEW"));
        review.withdraw("사업 취소");

        assertThat(ProgressSummary.of(List.of(firewall, review))).isEqualTo("2건 중 1건 협의 중, 1건 철회");
        assertThat(ProgressSummary.of(List.of())).isEqualTo("신청 없음");
    }
}
