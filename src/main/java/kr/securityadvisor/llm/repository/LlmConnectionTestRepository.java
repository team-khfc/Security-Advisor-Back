package kr.securityadvisor.llm.repository;

import kr.securityadvisor.llm.domain.LlmConnectionTest;
import org.springframework.data.jpa.repository.JpaRepository;

/** 관리자 연결 시험 기록 저장소. */
public interface LlmConnectionTestRepository extends JpaRepository<LlmConnectionTest, Long> {}
