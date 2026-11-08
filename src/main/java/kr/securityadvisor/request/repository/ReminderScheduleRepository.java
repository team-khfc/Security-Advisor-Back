package kr.securityadvisor.request.repository;

import java.util.Optional;
import kr.securityadvisor.request.domain.ReminderSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

/** 예약 작업 스케줄 저장소. */
public interface ReminderScheduleRepository extends JpaRepository<ReminderSchedule, Long> {

    Optional<ReminderSchedule> findByName(String name);
}
