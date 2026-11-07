package kr.securityadvisor.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.procedure.controller.ProcedureController;
import kr.securityadvisor.procedure.dto.ProcedureResponse;
import kr.securityadvisor.procedure.service.ProcedureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** 컨트롤러 단위 테스트: 서비스는 가짜(Mock)로 두고 HTTP 요청·응답 형식만 확인한다. 모든 API 는 로그인이 필요하므로 가짜 사용자로 요청한다. */
@WebMvcTest(ProcedureController.class)
@WithMockUser
class ProcedureControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProcedureService procedureService;

    @Test
    @DisplayName("절차 목록을 JSON 배열로 반환한다")
    void list() {
        given(procedureService.findActiveProcedures())
                .willReturn(List.of(new ProcedureResponse("SECURITY_REVIEW", "정보화사업 보안성검토", "설명")));

        assertThat(mvc.get().uri("/api/procedures"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].code")
                .isEqualTo("SECURITY_REVIEW");
    }

    @Test
    @DisplayName("없는 절차 코드는 404 ProblemDetail 을 반환한다")
    void getNotFound() {
        given(procedureService.getByCode("NONE")).willThrow(new NotFoundException("절차를 찾을 수 없습니다: NONE"));

        assertThat(mvc.get().uri("/api/procedures/NONE"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("절차를 찾을 수 없습니다: NONE");
    }
}
