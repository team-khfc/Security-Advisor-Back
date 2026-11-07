package kr.securityadvisor.common.security;

/**
 * 역할 4종(FUNCTIONAL_SPEC 3장). 여러 컨텍스트가 같이 쓰므로 common 에 둔다.
 */
public enum MemberRole {
    /** 일반 사용자: 자기 신청 생성·작성·보완 답변·최종 제출 */
    APPLICANT,
    /** 정보보안 담당자: 배정 신청의 보안 분야 검토 */
    SECURITY,
    /** ICT인프라 담당자: 배정 신청의 통신·인프라 분야 검토·자원 할당 */
    INFRA,
    /** 기관 관리자: 기관 설정·비공개 컨텍스트·LLM 구성 관리 (신청 검토 권한 없음) */
    ADMIN;

    /** Spring Security 권한 문자열. hasRole('ADMIN') 형태로 검사할 수 있도록 ROLE_ 접두사를 붙인다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
