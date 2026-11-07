package kr.securityadvisor.procedure.controller;

import java.util.List;
import kr.securityadvisor.procedure.dto.ProcedureResponse;
import kr.securityadvisor.procedure.service.ProcedureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보안 절차 API.
 */
@RestController
@RequestMapping("/api/procedures")
@RequiredArgsConstructor
public class ProcedureController {

    private final ProcedureService procedureService;

    /** 신청 가능한 절차 목록. */
    @GetMapping
    public List<ProcedureResponse> list() {
        return procedureService.findActiveProcedures();
    }

    /** 절차 한 건. 없으면 404(ProblemDetail). */
    @GetMapping("/{code}")
    public ProcedureResponse get(@PathVariable String code) {
        return procedureService.getByCode(code);
    }
}
