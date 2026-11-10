package kr.securityadvisor.request.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "infrastructure_plans")
public class InfrastructurePlan {
    @Id
    @Column(name = "security_request_id")
    private Long securityRequestId;

    private long revision;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InfrastructurePlan() {}

    public static InfrastructurePlan create(Long id) {
        var p = new InfrastructurePlan();
        p.securityRequestId = id;
        return p;
    }

    public void replace(String content, String actor, Instant at) {
        this.content = content;
        updatedBy = actor;
        updatedAt = at;
        revision++;
    }

    public long getRevision() {
        return revision;
    }

    public String getContent() {
        return content;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
