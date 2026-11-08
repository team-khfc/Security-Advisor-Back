package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 처리 기한(저장형, F-13). 기한일은 생길 때 한 번 계산해 저장하고 다시 계산하지 않는다(불변식 31).
 * 임박·지남은 저장하지 않고 {@link #statusOn} 으로 계산한다. 기한 경과는 상태를 바꾸지 않는다(불변식 22).
 * 재산정은 기존 행을 {@link #end} 로 종료하고 새 행을 만든다(불변식 23).
 */
@Entity
@Table(name = "deadlines")
public class Deadline extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deadline_id")
    private Long id;

    @Column(name = "security_request_id", nullable = false)
    private Long securityRequestId;

    /** REVIEW 에만 있다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReviewField field;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeadlineKind kind;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** 보완 대기로 정지된 기한인지(정지 = 종료 + 남은 영업일 보관, 재개 시 소비되어 false). */
    @Column(nullable = false)
    private boolean paused;

    @Column(name = "remaining_business_days")
    private Integer remainingBusinessDays;

    /** JPA 전용 기본 생성자. */
    protected Deadline() {}

    private Deadline(
            Long securityRequestId,
            ReviewField field,
            DeadlineKind kind,
            Instant startedAt,
            LocalDate dueDate,
            int policyVersion) {
        if ((kind == DeadlineKind.REVIEW) != (field != null)) {
            throw new DomainRuleViolationException("협의 기한에만 분야가 있어야 합니다: " + kind);
        }
        this.securityRequestId = securityRequestId;
        this.field = field;
        this.kind = kind;
        this.startedAt = startedAt;
        this.dueDate = dueDate;
        this.policyVersion = policyVersion;
        this.active = true;
    }

    /** 기산일 기준 영업일 수만큼 뒤를 기한일로 하는 새 기한. */
    public static Deadline start(
            Long securityRequestId,
            ReviewField field,
            DeadlineKind kind,
            Instant startedAt,
            LocalDate startDate,
            int businessDays,
            BusinessCalendar calendar,
            int policyVersion) {
        LocalDate dueDate = calendar.addBusinessDays(startDate, businessDays);
        return new Deadline(securityRequestId, field, kind, startedAt, dueDate, policyVersion);
    }

    public Long getId() {
        return id;
    }

    public Long getSecurityRequestId() {
        return securityRequestId;
    }

    public ReviewField getField() {
        return field;
    }

    public DeadlineKind getKind() {
        return kind;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getPolicyVersion() {
        return policyVersion;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    /** 오늘 기준 상태. 기한일 당일까지는 기한 내(임박 포함), 다음 날부터 지남. */
    public DeadlineStatus statusOn(LocalDate today, BusinessCalendar calendar, int imminentBusinessDays) {
        if (!active) {
            return DeadlineStatus.ENDED;
        }
        if (today.isAfter(dueDate)) {
            return DeadlineStatus.OVERDUE;
        }
        return calendar.businessDaysBetween(today, dueDate) <= imminentBusinessDays
                ? DeadlineStatus.IMMINENT
                : DeadlineStatus.ON_TRACK;
    }

    /**
     * 보완 대기 정지(F-13): 지금 남은 영업일을 보관하고 종료한다. 기한일은 바꾸지 않는다. 이미 지난 기한은 남은 일수 0 으로 보관한다.
     *
     * @return 남은 영업일
     */
    public int pause(Instant now, LocalDate today, BusinessCalendar calendar) {
        int remaining = today.isAfter(dueDate) ? 0 : Math.max(0, calendar.businessDaysBetween(today, dueDate));
        end(now);
        this.paused = true;
        this.remainingBusinessDays = remaining;
        return remaining;
    }

    /** 재개하면서 보관한 남은 영업일을 소비한다(두 번 재개되지 않게). */
    public int consumePause() {
        if (!paused || remainingBusinessDays == null) {
            throw new DomainRuleViolationException("정지된 기한이 아닙니다");
        }
        this.paused = false;
        return remainingBusinessDays;
    }

    public boolean isPaused() {
        return paused;
    }

    public Integer getRemainingBusinessDays() {
        return remainingBusinessDays;
    }

    /** 단계가 끝나거나 재산정될 때 종료한다. 기한일은 바꾸지 않는다. */
    public void end(Instant now) {
        if (!active) {
            throw new DomainRuleViolationException("이미 종료된 기한입니다");
        }
        this.active = false;
        this.endedAt = now;
    }
}
