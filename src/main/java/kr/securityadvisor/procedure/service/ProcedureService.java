package kr.securityadvisor.procedure.service;

import java.util.List;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.procedure.dto.ProcedureResponse;
import kr.securityadvisor.procedure.repository.ProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 절차 조회 기능.
 */
@Service
@Transactional(readOnly = true)
public class ProcedureService {

    private final ProcedureRepository procedureRepository;

    public ProcedureService(ProcedureRepository procedureRepository) {
        this.procedureRepository = procedureRepository;
    }

    /** 신청 가능한(활성화된) 절차 목록을 등록 순서대로 반환한다. */
    public List<ProcedureResponse> findActiveProcedures() {
        return procedureRepository.findAllByActiveTrueOrderByIdAsc().stream()
                .map(ProcedureResponse::from)
                .toList();
    }

    /**
     * 코드로 절차 하나를 조회한다.
     *
     * @throws NotFoundException 해당 코드의 절차가 없을 때
     */
    public ProcedureResponse getByCode(String code) {
        return procedureRepository
                .findByCode(code)
                .map(ProcedureResponse::from)
                .orElseThrow(() -> new NotFoundException("절차를 찾을 수 없습니다: " + code));
    }
}
