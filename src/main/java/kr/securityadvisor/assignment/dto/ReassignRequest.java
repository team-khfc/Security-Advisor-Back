package kr.securityadvisor.assignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 재배정 요청(기관 관리자). 새 배정을 만들고 이전 배정은 비활성으로 보존한다.
 *
 * @param field 분야
 * @param assigneeUsername 새 담당자(해당 분야 담당자 목록의 활성 구성원)
 * @param reason 사유(필수)
 */
public record ReassignRequest(
        @NotNull ReviewField field,
        @NotBlank @Size(max = 50) String assigneeUsername,
        @NotBlank @Size(max = 1000) String reason) {}
