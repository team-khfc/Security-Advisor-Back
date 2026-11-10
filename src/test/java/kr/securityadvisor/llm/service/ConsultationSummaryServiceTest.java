package kr.securityadvisor.llm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.common.error.ConflictException;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.dto.ConsultationOverview;
import kr.securityadvisor.request.dto.ConsultationOverview.Field;
import kr.securityadvisor.request.service.ConsultationComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ConsultationSummaryServiceTest {
    private final ConsultationComparisonService comparison = mock(ConsultationComparisonService.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final MemberPrincipal actor = new MemberPrincipal("applicant", "합성 신청자", MemberRole.APPLICANT);
    private final ConsultationSummaryService service = new ConsultationSummaryService(
            comparison, gateway, new InternalInfoGuard(), new ObjectMapper(), Clock.systemUTC());

    @BeforeEach
    void setup() {
        var fields = Map.of(
                "network:source", new Field("출발지", "통신", "192.0.2.10", true, ""),
                "network:pending", new Field("미확인 주소", "통신", "198.51.100.20", false, ""));
        when(comparison.prepare(actor, 1L))
                .thenReturn(new ConsultationOverview(
                        "version", true, null, "합성 사업", "합성 부서", Map.of(), fields, List.of(), null));
        when(comparison.saveSummary(eq(actor), eq(1L), any())).thenAnswer(call -> call.getArgument(2));
    }

    @Test
    @DisplayName("확인된 기관 사실을 모델에 제공하고 유효한 근거만 저장한다")
    void validSummary() {
        when(gateway.complete(any(), any(), any()))
                .thenReturn(new LlmResult(
                        "{\"items\":[{\"text\":\"확인된 출발지는 192.0.2.10입니다\",\"evidenceIds\":[\"E2\",\"E2\"]}]}",
                        "test/model"));
        var result = service.generate(actor, 1L, "version");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().evidenceIds()).containsExactly("network:source");
        var prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(gateway).complete(any(), prompt.capture(), any());
        assertThat(prompt.getValue().factLines()).contains("network:source: 192.0.2.10");
        assertThat(prompt.getValue().factLines()).noneMatch(line -> line.contains("198.51.100.20"));
        var catalog = new ObjectMapper().readTree(prompt.getValue().user()).path("evidenceCatalog");
        assertThat(catalog.path("E2").path("current").path("value").asText()).isEqualTo("192.0.2.10");
        assertThat(catalog.path("E1").path("current").path("confirmed").asBoolean())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"items\":[",
                "{\"items\":[]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[\"unknown\"]}]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[\"E999\"]}]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[\"network:source\"]}]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[\"current.E2\"]}]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[2]}]}",
                "{\"items\":[{\"text\":\"설명\",\"evidenceIds\":[]}]}",
                "{\"items\":[{\"text\":\"203.0.113.99로 연결\",\"evidenceIds\":[\"E2\"]}]}"
            })
    @DisplayName("불완전한 JSON·존재하지 않는 근거·근거 없는 IP는 저장하지 않는다")
    void invalidOutput(String output) {
        when(gateway.complete(any(), any(), any())).thenReturn(new LlmResult(output, "test/model"));
        assertThatThrownBy(() -> service.generate(actor, 1L, "version"))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(comparison, never()).saveSummary(any(), any(), any());
    }

    @Test
    @DisplayName("기준본에만 남은 항목과 담당자 변경 기록도 같은 근거 식별자로 연결한다")
    void baselineAndChangesUseSameIds() {
        var before = Map.of("removed:key", new Field("이전 서버", "서버", "192.0.2.30", true, "합성 자료"));
        var after = Map.of("new:key", new Field("새 서버", "서버", "192.0.2.40", true, "합성 자료"));
        var change = new ConsultationOverview.Change(
                7L, "UPDATED", "합성 담당자", "INFRA", java.time.Instant.EPOCH, before, after);
        when(comparison.prepare(actor, 1L))
                .thenReturn(new ConsultationOverview(
                        "version", true, null, "합성 사업", "합성 부서", before, after, List.of(change), null));
        when(gateway.complete(any(), any(), any()))
                .thenReturn(new LlmResult(
                        "{\"items\":[{\"text\":\"서버가 변경되었습니다\",\"evidenceIds\":[\"E2\",\"E1\"]}]}", "test/model"));
        var result = service.generate(actor, 1L, "version");
        assertThat(result.items().getFirst().evidenceIds()).containsExactly("removed:key", "new:key");
        var prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(gateway).complete(any(), prompt.capture(), any());
        var input = new ObjectMapper().readTree(prompt.getValue().user());
        assertThat(input.path("evidenceCatalog")
                        .path("E2")
                        .path("baseline")
                        .path("value")
                        .asText())
                .isEqualTo("192.0.2.30");
        assertThat(input.path("changes")
                        .get(0)
                        .path("before")
                        .path("E2")
                        .path("value")
                        .asText())
                .isEqualTo("192.0.2.30");
        assertThat(input.path("changes")
                        .get(0)
                        .path("after")
                        .path("E1")
                        .path("value")
                        .asText())
                .isEqualTo("192.0.2.40");
    }

    @Test
    @DisplayName("출력 한도 오류를 근거 오류로 바꾸거나 자동 재호출하지 않는다")
    void outputLimit() {
        when(gateway.complete(any(), any(), any())).thenThrow(new ServiceUnavailableException("모델 출력 한도"));
        assertThatThrownBy(() -> service.generate(actor, 1L, "version")).hasMessage("모델 출력 한도");
        verify(gateway, times(1)).complete(any(), any(), any());
        verify(comparison, never()).saveSummary(any(), any(), any());
    }

    @Test
    @DisplayName("원본이 달라지면 모델을 호출하지 않는다")
    void staleInput() {
        assertThatThrownBy(() -> service.generate(actor, 1L, "old")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(gateway);
    }
}
