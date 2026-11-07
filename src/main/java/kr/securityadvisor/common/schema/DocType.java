package kr.securityadvisor.common.schema;

/** 서류 유형(1차 범위 2종). 사업 건 안에서 유형당 신청은 하나다(불변식 14). */
public enum DocType {
    /** 방화벽 정책 허용 신청서 */
    FIREWALL,
    /** 정보화사업 보안성검토 신청서 */
    SECURITY_REVIEW
}
