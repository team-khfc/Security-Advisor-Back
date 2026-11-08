package kr.securityadvisor.configuration.repository;

import java.util.Optional;
import kr.securityadvisor.configuration.domain.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;

/** 설정 초안 저장소(설치당 1건). */
public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    Optional<Configuration> findFirstByOrderByIdAsc();
}
