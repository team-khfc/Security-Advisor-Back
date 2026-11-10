package kr.securityadvisor.common.schema;

/** 계정 또는 분야 등록 변경 후 진행 중인 업무의 인계 필요 여부를 다시 확인한다. */
public record ReviewerEligibilityChanged(String username, String eventKey) {}
