package kr.securityadvisor.knowledge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 운영 기관 비공개 컨텍스트(사실, F-09): 서버·망·자산 레코드. 문서가 아니라 구조화된 행이며 AI 는 서버가 뽑아 준 행만 참조한다(원칙 3).
 * 신청자(APPLICANT)에게는 어떤 행도 보이지 않는다(불변식: visible_roles 에 APPLICANT 금지).
 */
@Entity
@Table(name = "private_contexts")
public class PrivateContext extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "private_context_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrivateContextKind kind;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String aliases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String attributes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visible_roles", nullable = false)
    private String visibleRoles;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long revision;

    protected PrivateContext() {}

    private PrivateContext(
            PrivateContextKind kind,
            String name,
            String aliasesJson,
            String attributesJson,
            String visibleRolesJson,
            String note) {
        this.kind = kind;
        this.name = name;
        this.aliases = aliasesJson;
        this.attributes = attributesJson;
        this.visibleRoles = visibleRolesJson;
        this.note = note;
    }

    /**
     * @param visibleRolesIncludeApplicant 호출자가 미리 계산 — true 면 거부
     */
    public static PrivateContext create(
            PrivateContextKind kind,
            String name,
            String aliasesJson,
            String attributesJson,
            String visibleRolesJson,
            boolean visibleRolesIncludeApplicant,
            String note) {
        validate(kind, name, visibleRolesIncludeApplicant);
        return new PrivateContext(kind, name.strip(), aliasesJson, attributesJson, visibleRolesJson, note);
    }

    public void update(
            PrivateContextKind kind,
            String name,
            String aliasesJson,
            String attributesJson,
            String visibleRolesJson,
            boolean visibleRolesIncludeApplicant,
            String note,
            boolean active) {
        validate(kind, name, visibleRolesIncludeApplicant);
        this.kind = kind;
        this.name = name.strip();
        this.aliases = aliasesJson;
        this.attributes = attributesJson;
        this.visibleRoles = visibleRolesJson;
        this.note = note;
        this.active = active;
    }

    public void requireRevision(long expectedRevision) {
        if (expectedRevision != revision) {
            throw new ConflictException("비공개 정보가 다른 곳에서 수정되었습니다(수정본 " + revision + ")");
        }
    }

    private static void validate(PrivateContextKind kind, String name, boolean visibleRolesIncludeApplicant) {
        if (kind == null) {
            throw new DomainRuleViolationException("종류가 필요합니다");
        }
        if (name == null || name.isBlank()) {
            throw new DomainRuleViolationException("이름이 필요합니다");
        }
        if (visibleRolesIncludeApplicant) {
            throw new DomainRuleViolationException("비공개 컨텍스트는 신청자(APPLICANT)에게 보일 수 없습니다");
        }
    }

    public Long getId() {
        return id;
    }

    public PrivateContextKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getAliases() {
        return aliases;
    }

    public String getAttributes() {
        return attributes;
    }

    public String getVisibleRoles() {
        return visibleRoles;
    }

    public String getNote() {
        return note;
    }

    public boolean isActive() {
        return active;
    }

    public long getRevision() {
        return revision;
    }
}
