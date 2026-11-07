package kr.securityadvisor.assignment.dto;

import java.util.List;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 자동 배정 결과. 미배정 분야는 협의를 시작할 수 없고 기관 관리자에게 표시한다(3-3장).
 *
 * @param assigned 배정된 분야
 * @param unassigned 기본 담당자가 없거나 신청자 본인이라 배정하지 못한 분야
 */
public record AssignmentSummary(List<AssignmentResponse> assigned, List<ReviewField> unassigned) {}
