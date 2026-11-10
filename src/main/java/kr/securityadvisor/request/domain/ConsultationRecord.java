package kr.securityadvisor.request.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consultation_records")
public class ConsultationRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consultation_record_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    protected ConsultationRecord() {}

    public ConsultationRecord(
            Long requestId, String kind, String actorName, String actorRole, Instant at, String payload) {
        this.requestId = requestId;
        this.kind = kind;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.createdAt = at;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPayload() {
        return payload;
    }
}
