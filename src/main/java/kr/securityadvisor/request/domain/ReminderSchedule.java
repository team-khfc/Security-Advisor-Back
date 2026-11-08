package kr.securityadvisor.request.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.securityadvisor.common.entity.BaseTimeEntity;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import org.springframework.scheduling.support.CronExpression;

/** 예약 작업 스케줄(기한 알림). 이름당 한 행이며 기관 관리자가 바꾼다. 시간대는 Asia/Seoul 고정. */
@Entity
@Table(name = "reminder_schedules")
public class ReminderSchedule extends BaseTimeEntity {

    /** 기한 알림 스케줄 이름. */
    public static final String DEADLINE = "DEADLINE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_schedule_id")
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /** JPA 전용 기본 생성자. 행은 seed 로만 만들어진다. */
    protected ReminderSchedule() {}

    /**
     * 스케줄을 바꾼다. cron 은 Spring 6필드 형식이어야 한다.
     *
     * @throws DomainRuleViolationException cron 형식 오류(422)
     */
    public void change(String cron, boolean enabled, String updatedBy) {
        String normalized = cron == null ? "" : cron.strip();
        if (!CronExpression.isValidExpression(normalized)) {
            throw new DomainRuleViolationException("cron 형식이 올바르지 않습니다(초 분 시 일 월 요일, 예: 0 10 8 * * *): " + cron);
        }
        this.cronExpression = normalized;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
