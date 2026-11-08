package kr.securityadvisor.audit.domain;

/** 감사 대상 유형. target_id 는 대상의 기본 키 문자열(사용자는 username). */
public enum AuditTargetType {
    MEMBER,
    BUSINESS_CASE,
    SECURITY_REQUEST,
    ANSWER,
    ASSIGNMENT,
    DEADLINE,
    KNOWLEDGE_SNIPPET,
    PRIVATE_CONTEXT,
    LLM_CONFIGURATION
}
