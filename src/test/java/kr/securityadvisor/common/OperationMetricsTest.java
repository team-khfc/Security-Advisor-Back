package kr.securityadvisor.common;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.util.List;
import kr.securityadvisor.common.observability.OperationMetrics;
import kr.securityadvisor.common.observability.OperationMetrics.Operation;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.*;
import kr.securityadvisor.notification.service.*;
import org.junit.jupiter.api.Test;

class OperationMetricsTest {
    @Test
    void AI_성공과_감싼_시간초과를_측정하되_원문과_예외를_바꾸지_않는다() {
        var registry = new SimpleMeterRegistry();
        var metrics = new OperationMetrics(registry);
        var backend = mock(LlmBackend.class);
        var writer = mock(AuditingLlmGateway.LlmCallAuditWriter.class);
        var gateway = new AuditingLlmGateway(metrics, backend, writer);
        var prompt = LlmPrompt.of("합성 비밀 원문", List.of(), List.of(), "합성 사용자 입력");
        var result = new LlmResult("합성 응답", "synthetic-model");
        when(backend.complete(LlmPurpose.INTERVIEW, prompt)).thenReturn(result);
        assertThat(gateway.complete(LlmPurpose.INTERVIEW, prompt, null)).isSameAs(result);
        var failure = new IllegalStateException("합성 키는 지표에 넣지 않음", new SocketTimeoutException());
        when(backend.complete(LlmPurpose.INTERVIEW, prompt)).thenThrow(failure);
        assertThatThrownBy(() -> gateway.complete(LlmPurpose.INTERVIEW, prompt, null))
                .isSameAs(failure);
        assertThat(registry.get("securityadvisor.llm.executions")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.get("securityadvisor.llm.executions")
                        .tag("outcome", "timeout")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(registry.getMeters().toString()).doesNotContain("합성", "synthetic-model");
    }

    @Test
    void 알림_실패는_업무를_중단하지_않고_꺼진_채널은_측정하지_않는다() {
        var registry = new SimpleMeterRegistry();
        var enabled = mock(NotificationChannel.class);
        var disabled = mock(NotificationChannel.class);
        when(enabled.enabled()).thenReturn(true);
        when(enabled.name()).thenReturn("WEBHOOK");
        var notification = kr.securityadvisor.notification.domain.Notification.of(
                "synthetic",
                "synthetic",
                kr.securityadvisor.notification.domain.NotificationKind.ASSIGNED,
                "합성 알림",
                "/requests/1");
        doThrow(new IllegalStateException("합성 실패")).when(enabled).deliver(notification);
        var dispatcher = new ExternalNotificationDispatcher(List.of(enabled, disabled), new OperationMetrics(registry));
        assertThatCode(() -> dispatcher.dispatch(notification)).doesNotThrowAnyException();
        verify(disabled, never()).deliver(any());
        assertThat(registry.get("securityadvisor.notification.executions")
                        .tag("outcome", "failure")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void 예약실행_성공시각과_실패_중복제외_지표가_분리된다() {
        var registry = new SimpleMeterRegistry();
        var metrics = new OperationMetrics(registry);
        metrics.measure(Operation.REMINDER, "scheduled", () -> "ok");
        double success =
                registry.get("securityadvisor.reminder.last.success").gauge().value();
        assertThat(success).isPositive();
        assertThatThrownBy(() -> metrics.measure(Operation.REMINDER, "scheduled", () -> {
            throw new IllegalStateException();
        }));
        assertThat(registry.get("securityadvisor.reminder.last.success").gauge().value())
                .isEqualTo(success);
        metrics.measure(Operation.NOTIFICATION, "in_app", () -> null);
        assertThat(registry.get("securityadvisor.notification.executions")
                        .tag("outcome", "skipped")
                        .timer()
                        .count())
                .isEqualTo(1);
    }
}
