package kr.securityadvisor.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditEvent;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.repository.AuditEventRepository;
import kr.securityadvisor.common.calendar.BusinessCalendar;
import kr.securityadvisor.common.schema.DeadlinePolicy;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.insight.service.InsightService;
import kr.securityadvisor.policy.service.FirewallRuleService;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.Deadline;
import kr.securityadvisor.request.domain.DeadlineKind;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.DeadlineRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.review.repository.SupplementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InsightPolicyTest {
    @Test
    @DisplayName("같은 기한일과 단계 이력도 각 사업의 고정 공휴일로 계산하며 현재 게시본을 참조하지 않는다")
    void pinnedCalendars() {
        var requests = mock(SecurityRequestRepository.class);
        var cases = mock(BusinessCaseRepository.class);
        var deadlines = mock(DeadlineRepository.class);
        var supplements = mock(SupplementRepository.class);
        var audit = mock(AuditEventRepository.class);
        var firewall = mock(FirewallRuleService.class);
        var publications = mock(PublicationService.class);
        var normal = new FieldSchema(new DeadlinePolicy(5, 5, 5, 1, Set.of()), List.of(), List.of());
        var holiday = new FieldSchema(
                new DeadlinePolicy(5, 5, 5, 1, Set.of(LocalDate.parse("2026-11-10"))), List.of(), List.of());
        when(publications.getFieldSchema(11L)).thenReturn(normal);
        when(publications.getFieldSchema(22L)).thenReturn(holiday);
        var first = mock(BusinessCase.class);
        var second = mock(BusinessCase.class);
        when(first.getId()).thenReturn(1L);
        when(first.getPublicationId()).thenReturn(11L);
        when(second.getId()).thenReturn(2L);
        when(second.getPublicationId()).thenReturn(22L);
        when(cases.findAll()).thenReturn(List.of(first, second));
        var one = mock(SecurityRequest.class);
        var two = mock(SecurityRequest.class);
        when(one.getId()).thenReturn(101L);
        when(one.getBusinessCaseId()).thenReturn(1L);
        when(two.getId()).thenReturn(102L);
        when(two.getBusinessCaseId()).thenReturn(2L);
        for (var r : List.of(one, two)) {
            when(r.getDocType()).thenReturn(new DocTypeCode("FIREWALL"));
            when(r.getStatus()).thenReturn(RequestStatus.IN_REVIEW);
            when(r.getStage()).thenReturn(2);
        }
        when(requests.findAll()).thenReturn(List.of(one, two));
        Instant start = Instant.parse("2026-11-09T00:00:00Z"), end = Instant.parse("2026-11-11T00:00:00Z");
        when(deadlines.findByActiveTrue())
                .thenReturn(List.of(
                        Deadline.start(
                                101L,
                                ReviewField.INFRA,
                                DeadlineKind.REVIEW,
                                start,
                                LocalDate.parse("2026-11-09"),
                                2,
                                new BusinessCalendar(Set.of()),
                                11),
                        Deadline.start(
                                102L,
                                ReviewField.INFRA,
                                DeadlineKind.REVIEW,
                                start,
                                LocalDate.parse("2026-11-09"),
                                2,
                                new BusinessCalendar(Set.of()),
                                22)));
        when(audit.findByTargetTypeAndActionInOrderByOccurredAtAscIdAsc(any(), anyList()))
                .thenReturn(List.of(
                        event("101", start, AuditAction.SECURITY_REQUEST_CREATED),
                                event("102", start, AuditAction.SECURITY_REQUEST_CREATED),
                        event("101", end, AuditAction.REVIEW_REQUESTED),
                                event("102", end, AuditAction.REVIEW_REQUESTED)));
        var service = new InsightService(
                requests,
                cases,
                deadlines,
                supplements,
                audit,
                firewall,
                publications,
                Clock.fixed(start, ZoneOffset.UTC));
        var result = service.summary(new MemberPrincipal("admin", "합성 관리자", MemberRole.ADMIN));
        assertThat(result.averageBusinessDaysByStage().get("1")).isEqualTo(1.5);
        assertThat(result.completedByStage().get("1")).isEqualTo(2L);
        assertThat(result.activeDeadlines())
                .containsEntry("ON_TRACK", 1L)
                .containsEntry("IMMINENT", 1L)
                .containsEntry("OVERDUE", 0L);
        assertThat(result.aiProposalConfirmRate()).isNull();
        verify(publications).getFieldSchema(11L);
        verify(publications).getFieldSchema(22L);
        verifyNoMoreInteractions(publications);
        verify(firewall).list(any(), isNull());
        verifyNoMoreInteractions(firewall);
    }

    private AuditEvent event(String id, Instant at, AuditAction action) {
        return AuditEvent.of(
                at,
                "admin",
                MemberRole.ADMIN,
                AuditTargetType.SECURITY_REQUEST,
                id,
                0L,
                action,
                null,
                "합성",
                "synthetic");
    }
}
