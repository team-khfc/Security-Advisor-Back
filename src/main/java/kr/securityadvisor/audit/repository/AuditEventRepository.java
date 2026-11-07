package kr.securityadvisor.audit.repository;

import java.util.List;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 감사 이력 저장소. save 와 조회만 쓴다 — 수정·삭제는 DB 트리거가 거부한다. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByTargetTypeAndTargetIdOrderByOccurredAtAscIdAsc(AuditTargetType targetType, String targetId);

    Page<AuditEvent> findByTargetTypeAndTargetId(AuditTargetType targetType, String targetId, Pageable pageable);

    Page<AuditEvent> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
