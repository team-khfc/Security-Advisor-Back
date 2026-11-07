package kr.securityadvisor.procedure.dto;

import kr.securityadvisor.procedure.domain.Procedure;

/**
 * 보안 절차 응답. 엔티티를 API 로 직접 내보내지 않고 이 DTO 로 바꿔서 응답한다.
 *
 * @param code 절차 코드 (예: SECURITY_REVIEW)
 * @param name 화면에 보여줄 이름
 * @param description 절차 설명
 */
public record ProcedureResponse(String code, String name, String description) {

    public static ProcedureResponse from(Procedure procedure) {
        return new ProcedureResponse(procedure.getCode(), procedure.getName(), procedure.getDescription());
    }
}
