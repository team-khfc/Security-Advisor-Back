package kr.securityadvisor.llm.repository;

import kr.securityadvisor.llm.domain.LlmConfigurationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 구성별 순서가 고정된 변경 이력 조회. */
public interface LlmConfigurationHistoryRepository extends JpaRepository<LlmConfigurationHistory, Long> {
    Page<LlmConfigurationHistory> findByConfigurationIdOrderByIdDesc(Long configurationId, Pageable pageable);
}
