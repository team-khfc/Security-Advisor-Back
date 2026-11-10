package kr.securityadvisor.llm.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.llm.domain.LlmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;

/** LLM 구성 저장소. */
public interface LlmConfigurationRepository extends JpaRepository<LlmConfiguration, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from LlmConfiguration c where c.id = :id")
    Optional<LlmConfiguration> lockById(Long id);

    List<LlmConfiguration> findByActiveTrueOrderByPriorityAscIdAsc();

    List<LlmConfiguration> findAllByOrderByPriorityAscIdAsc();

    Optional<LlmConfiguration> findByName(String name);
}
