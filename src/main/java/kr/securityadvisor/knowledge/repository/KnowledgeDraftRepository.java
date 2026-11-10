package kr.securityadvisor.knowledge.repository;

import jakarta.persistence.LockModeType;
import kr.securityadvisor.knowledge.domain.KnowledgeDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface KnowledgeDraftRepository extends JpaRepository<KnowledgeDraft, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from KnowledgeDraft d where d.id = 1")
    KnowledgeDraft lockCurrent();
}
