package kr.securityadvisor.assignment.dto;

import java.util.List;

/**
 * 담당자 비활성화 결과. 그 사람이 활성 배정을 갖고 있던 신청은 "재배정 필요"로 돌려준다 — 배정 자체는 바꾸지 않는다
 * (재배정은 기관 관리자가 사유와 함께 따로 한다, F-12).
 *
 * @param reassignmentNeededRequestIds 이 담당자에게 활성 배정이 남아 있는 신청 ID
 */
public record ReviewerPoolDeactivationResult(ReviewerPoolResponse pool, List<Long> reassignmentNeededRequestIds) {}
