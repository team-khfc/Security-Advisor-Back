package kr.securityadvisor.request.repository;

import java.util.List;
import kr.securityadvisor.request.domain.ConsultationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationRecordRepository extends JpaRepository<ConsultationRecord, Long> {
    List<ConsultationRecord> findByRequestIdOrderByIdAsc(Long requestId);
}
