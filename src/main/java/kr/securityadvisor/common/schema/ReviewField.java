package kr.securityadvisor.common.schema;

import kr.securityadvisor.common.security.MemberRole;

/** 협의 분야. 2단계 담당자 협의는 분야별로 독립이며 1차 범위는 INFRA 선행 → SECURITY 후행 고정. */
public enum ReviewField {
    /** ICT인프라 — 서버·IP·포트 할당, 통신·인프라 검토 */
    INFRA(MemberRole.INFRA),
    /** 정보보안 — 보안 검토 */
    SECURITY(MemberRole.SECURITY);

    private final MemberRole role;

    ReviewField(MemberRole role) {
        this.role = role;
    }

    /** 이 분야의 담당자가 가져야 하는 역할. */
    public MemberRole role() {
        return role;
    }

    public static ReviewField of(MemberRole role) {
        for (ReviewField field : values()) {
            if (field.role == role) {
                return field;
            }
        }
        throw new IllegalArgumentException("협의 분야가 아닌 역할입니다: " + role);
    }
}
