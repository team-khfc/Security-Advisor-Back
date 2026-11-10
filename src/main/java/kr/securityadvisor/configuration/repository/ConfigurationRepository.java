package kr.securityadvisor.configuration.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kr.securityadvisor.configuration.domain.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 설정 초안 저장소(설치당 1건). */
public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

    Optional<Configuration> findFirstByOrderByIdAsc();

    /** 저장·게시를 같은 초안 행에서 직렬화하고, 잠금 획득 후 revision을 검사한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Configuration c where c.id = :id")
    Optional<Configuration> findForUpdate(Long id);
}
