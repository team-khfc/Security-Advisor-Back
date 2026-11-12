package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.schema.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.notification.service.Notifier;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.DeadlineReminderRunCommand;
import kr.securityadvisor.request.repository.*;
import kr.securityadvisor.request.service.*;
import org.junit.jupiter.api.Test;

class DeadlinePolicyReminderTest {
    @Test
    void 게시본의_3영업일과_휴일을_알림에_적용하고_당일과_지연실행도_놓치지_않는다() {
        var deadlines = mock(DeadlineRepository.class);
        var requests = mock(SecurityRequestRepository.class);
        var cases = mock(BusinessCaseRepository.class);
        var publications = mock(PublicationService.class, RETURNS_DEEP_STUBS);
        var assignments = mock(AssignmentService.class);
        var notifications = mock(Notifier.class);
        var extra = mock(OperationalWork.class);
        var request = mock(SecurityRequest.class);
        var business = mock(BusinessCase.class);
        var deadline = mock(Deadline.class);
        when(deadlines.findByActiveTrue()).thenReturn(List.of(deadline));
        when(deadline.getSecurityRequestId()).thenReturn(1L);
        when(deadline.getKind()).thenReturn(DeadlineKind.REVIEW);
        when(deadline.getField()).thenReturn(ReviewField.INFRA);
        when(deadline.getDueDate()).thenReturn(LocalDate.of(2026, 11, 20));
        when(requests.findById(1L)).thenReturn(Optional.of(request));
        when(request.getId()).thenReturn(1L);
        when(request.getBusinessCaseId()).thenReturn(2L);
        when(cases.findById(2L)).thenReturn(Optional.of(business));
        when(business.getPublicationId()).thenReturn(3L);
        when(publications.get(3L).fieldSchema())
                .thenReturn(new FieldSchema(
                        new DeadlinePolicy(5, 5, 5, 3, Set.of(LocalDate.of(2026, 11, 18))), List.of(), List.of()));
        var service = new DeadlineReminderService(
                deadlines,
                requests,
                cases,
                publications,
                assignments,
                notifications,
                Clock.systemUTC(),
                List.of(extra));
        var admin = new MemberPrincipal("synthetic-admin", "합성 관리자", MemberRole.ADMIN);
        assertThat(service.runAs(admin, new DeadlineReminderRunCommand(LocalDate.of(2026, 11, 13), 1L))
                        .imminent())
                .isZero();
        assertThat(service.runAs(admin, new DeadlineReminderRunCommand(LocalDate.of(2026, 11, 16), 1L))
                        .imminent())
                .isEqualTo(1);
        assertThat(service.runAs(admin, new DeadlineReminderRunCommand(LocalDate.of(2026, 11, 19), 1L))
                        .imminent())
                .isEqualTo(1);
        assertThat(service.runAs(admin, new DeadlineReminderRunCommand(LocalDate.of(2026, 11, 20), 1L))
                        .imminent())
                .isEqualTo(1);
        assertThat(service.runAs(admin, new DeadlineReminderRunCommand(LocalDate.of(2026, 11, 21), 1L))
                        .overdue())
                .isEqualTo(1);
        verify(extra).remind(LocalDate.of(2026, 11, 21), 1L);
    }
}
