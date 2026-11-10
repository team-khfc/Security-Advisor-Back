package kr.securityadvisor.common.schema;

/** 배정 이력을 유지하면서 현재 업무 인계가 필요한 이유를 표현한다. 저장 상태가 아닌 조회 시점의 판정이다. */
public enum ReassignmentReason {
    UNASSIGNED,
    ACCOUNT_INACTIVE,
    ROLE_CHANGED,
    NOT_REGISTERED,
    POOL_INACTIVE
}
