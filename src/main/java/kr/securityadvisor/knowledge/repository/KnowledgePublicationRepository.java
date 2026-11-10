package kr.securityadvisor.knowledge.repository;

import java.util.Optional;
import kr.securityadvisor.knowledge.domain.KnowledgePublication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgePublicationRepository extends JpaRepository<KnowledgePublication, Long> {
    Optional<KnowledgePublication> findFirstByOrderByVersionDesc();

    Optional<KnowledgePublication> findByVersion(int version);
}
