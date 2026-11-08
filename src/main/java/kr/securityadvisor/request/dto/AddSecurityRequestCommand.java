package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotNull;
import kr.securityadvisor.common.schema.DocTypeCode;

/**
 * 기존 사업 건에 서류(신청) 추가(F-01 P2). 사업 건 생성 시점 게시본의 활성 유형이어야 하고(불변식 8),
 * 같은 유형의 진행 중(종료 아닌) 신청이 이미 있으면 거부한다.
 *
 * @param docType 추가할 서류 유형
 * @param renewsFirewallRuleId 방화벽 규칙 연장 신청이면 원 규칙 ID(policy 컨텍스트가 넣는다, API 로는 보통 null)
 */
public record AddSecurityRequestCommand(@NotNull DocTypeCode docType, Long renewsFirewallRuleId) {

    public AddSecurityRequestCommand(DocTypeCode docType) {
        this(docType, null);
    }
}
