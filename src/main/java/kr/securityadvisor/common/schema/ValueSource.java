package kr.securityadvisor.common.schema;

/**
 * 값의 출처 4종(불변 원칙 2). 어떤 출처에도 없는 값은 "담당자 확인 필요" 상태이며 이 열거형이 아니라 null 로 표현한다.
 */
public enum ValueSource {
    /** 사용자(신청자) 답변 */
    USER_ANSWER,
    /** AI 추론 — 근거가 있어야 하고 확인 전까지 미확정 */
    AI_INFERRED,
    /** 담당자 입력(예: INFRA 가 할당한 서버·IP·포트) */
    STAFF_INPUT,
    /** 담당자가 확인한 값 — 확인 주체 없이는 전이 불가(불변식 5) */
    STAFF_CONFIRMED
}
