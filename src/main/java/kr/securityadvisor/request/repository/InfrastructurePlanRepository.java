package kr.securityadvisor.request.repository;

import kr.securityadvisor.request.domain.InfrastructurePlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InfrastructurePlanRepository extends JpaRepository<InfrastructurePlan, Long> {}
