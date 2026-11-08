package kr.securityadvisor.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 방화벽 규칙 연장 신청(F-15). 기존 규칙은 바뀌지 않고, 규칙 값(출발지·목적지·프로토콜·포트)과 원 신청의 답변을 미리 채운
 * 새 방화벽 신청이 같은 사업 건에 1단계로 생긴다. 신청자는 기간·목적을 고치고 평소처럼 협의·제출한다.
 *
 * @param newPeriod 새 사용 기간 "YYYY-MM-DD~YYYY-MM-DD"(상시면 종료일 생략 가능)
 * @param note 연장 사유(선택, 서류의 허용 목적에 덧붙인다)
 */
public record RuleRenewalCommand(
        @NotBlank @Size(max = 30) String newPeriod,
        @Size(max = 1000) String note) {}
