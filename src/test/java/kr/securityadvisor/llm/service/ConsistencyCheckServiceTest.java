package kr.securityadvisor.llm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConsistencyCheckServiceTest {
    private final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final ConsistencyCheckService service = new ConsistencyCheckService(
            access, null, null, null, null, gateway, new InternalInfoGuard(), new ObjectMapper(), null, null);
    private final List<AnswerResponse> answers = List.of(new AnswerResponse(
            1L, "data.personal_info", AnswerScope.CASE, "false", null, null, false, null, null, false, 1, false));

    @Test
    void 실제_원문_인용과_답변을_함께_반환한다() {
        var result =
                service.validate("""
            {"findings":[{"fieldKey":"data.personal_info","fileId":2,"quote":"성명을 수집한다","explanation":"개인정보 처리 여부를 확인해 주세요."}]}
            """, answers, Map.of("data.personal_info", "개인정보 처리"), Map.of(2L, "예약자의 성명을 수집한다."));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().answer()).isEqualTo("false");
    }

    @Test
    void 가짜_인용과_다른_파일과_없는_질문은_거부한다() {
        for (String output : List.of(
                "{\"findings\":[{\"fieldKey\":\"data.personal_info\",\"fileId\":2,\"quote\":\"없는 문장\",\"explanation\":\"확인\"}]}",
                "{\"findings\":[{\"fieldKey\":\"data.personal_info\",\"fileId\":99,\"quote\":\"성명\",\"explanation\":\"확인\"}]}",
                "{\"findings\":[{\"fieldKey\":\"unknown.key\",\"fileId\":2,\"quote\":\"성명\",\"explanation\":\"확인\"}]}")) {
            assertThatThrownBy(() -> service.validate(output, answers, Map.of(), Map.of(2L, "성명")))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        assertThat(service.validate("{\"findings\":[]}", answers, Map.of(), Map.of(2L, "성명")))
                .isEmpty();
    }

    @Test
    void 조회_권한_없으면_모델을_호출하지_않는다() {
        when(access.viewableRequest(null, 99L)).thenThrow(new NotFoundException("없음"));
        assertThatThrownBy(() -> service.check(null, 99L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void 근거_없는_주소와_잘못된_형식은_성공으로_표시하지_않는다() {
        for (String output : List.of(
                "{}",
                "not json",
                "{\"findings\":null}",
                "{\"findings\":[{\"fieldKey\":\"data.personal_info\",\"fileId\":2,\"quote\":\"성명\",\"explanation\":\"192.0.2.50 서버 확인\"}]}")) {
            assertThatThrownBy(() -> service.validate(output, answers, Map.of(), Map.of(2L, "성명")))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
    }
}
