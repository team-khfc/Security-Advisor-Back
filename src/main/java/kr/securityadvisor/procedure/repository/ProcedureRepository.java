package kr.securityadvisor.procedure.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.procedure.domain.Procedure;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 보안 절차 저장소.
 */
public interface ProcedureRepository extends JpaRepository<Procedure, Long> {

    List<Procedure> findAllByActiveTrueOrderByIdAsc();

    Optional<Procedure> findByCode(String code);
}
