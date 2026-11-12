package kr.securityadvisor.request.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.repository.PreparationMaterialsRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PreparationMaterialsServiceTest {
    private final PreparationMaterialsRepository repository = mock(PreparationMaterialsRepository.class);
    private final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    private final AnswerService answers = mock(AnswerService.class);
    private final UploadedFileService files = mock(UploadedFileService.class);
    private final SecurityRequest request = mock(SecurityRequest.class);
    private final BusinessCase business = mock(BusinessCase.class);
    private final PreparationMaterials stored = PreparationMaterials.create(1L);
    private final kr.securityadvisor.notification.service.Notifier notifier =
            mock(kr.securityadvisor.notification.service.Notifier.class);
    private final kr.securityadvisor.assignment.service.AssignmentService assignments =
            mock(kr.securityadvisor.assignment.service.AssignmentService.class);
    private final PreparationMaterialsService service = new PreparationMaterialsService(
            repository,
            access,
            answers,
            files,
            mock(AuditRecorder.class),
            mock(EntityManager.class),
            new ObjectMapper(),
            Clock.systemUTC(),
            assignments,
            notifier);
    private final MemberPrincipal owner = new MemberPrincipal("owner", "합성 신청자", MemberRole.APPLICANT);
    private final MemberPrincipal security = new MemberPrincipal("security", "합성 보안", MemberRole.SECURITY);
    private final MemberPrincipal infra = new MemberPrincipal("infra", "합성 인프라", MemberRole.INFRA);

    PreparationMaterialsServiceTest() {
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(access.viewableRequest(any(), eq(1L))).thenReturn(request);
        when(access.viewableCase(any(), eq(2L))).thenReturn(business);
        when(request.getBusinessCaseId()).thenReturn(2L);
        when(request.getId()).thenReturn(1L);
        when(business.getApplicantUsername()).thenReturn("owner");
        when(business.getId()).thenReturn(2L);
        when(request.isUnsubmitted()).thenReturn(true);
        when(request.getStage()).thenReturn(2);
        when(answers.allAnswers(request)).thenReturn(List.of());
    }

    @Test
    void 최종검토_묶음은_파일명과_기록을_고정하고_조건변경으로_기존묶음을_바꾸지_않는다() {
        when(files.list(owner, 2L))
                .thenReturn(List.of(new UploadedFileResponse(
                        7L, 2L, "합성-자료.txt", "text/plain", 10, "owner", java.time.Instant.EPOCH)));
        service.update(owner, 1L, command(owner, "ATTACH", "plan", null, "첨부", null));
        service.update(security, 1L, command(security, "REVIEW", "plan", "CONFIRMED", "원문 확인", null));
        var snapshot = service.finalReview(owner, 1L);
        assertThat(snapshot.items().getFirst().status()).isEqualTo("CONFIRMED");
        assertThat(snapshot.items().getFirst().files().getFirst().name()).isEqualTo("합성-자료.txt");
        service.update(owner, 1L, command(owner, "CONDITIONS", null, null, null, Map.of("cloud", true)));
        assertThat(service.finalReview(owner, 1L).items().getFirst().stale()).isTrue();
        assertThat(snapshot.items().getFirst().stale()).isFalse();
    }

    @Test
    void 자료보완은_신청자에게_재첨부는_해당분야_현재배정자에게_알린다() {
        when(assignments.listForRequest(1L))
                .thenReturn(List.of(new kr.securityadvisor.assignment.dto.AssignmentResponse(
                        1L,
                        1L,
                        kr.securityadvisor.common.schema.ReviewField.SECURITY,
                        "security",
                        true,
                        null,
                        "admin",
                        java.time.Instant.EPOCH,
                        null,
                        null)));
        service.update(owner, 1L, command(owner, "ATTACH", "plan", null, "첨부", null));
        verify(notifier)
                .notify(
                        eq(List.of("security")),
                        anyString(),
                        eq(kr.securityadvisor.notification.domain.NotificationKind.PREPARATION_REVIEW),
                        anyString(),
                        eq("/requests/1"));
        service.update(security, 1L, command(security, "REVIEW", "plan", "NEEDS_SUPPLEMENT", "범위 보완", null));
        verify(notifier)
                .notify(
                        eq("owner"),
                        anyString(),
                        eq(kr.securityadvisor.notification.domain.NotificationKind.PREPARATION_SUPPLEMENT),
                        anyString(),
                        eq("/requests/1"));
    }

    private PreparationMaterialsCommand command(
            MemberPrincipal actor,
            String action,
            String key,
            String status,
            String note,
            Map<String, Boolean> conditions) {
        var current = service.get(actor, 1L);
        return new PreparationMaterialsCommand(
                current.revision(), current.fingerprint(), action, key, status, List.of(7L), note, conditions);
    }

    @Test
    void 미확인_조건을_미해당으로_추정하지_않는다() {
        var items = service.get(owner, 1L).items();
        assertThat(items).hasSize(10);
        assertThat(items.stream()
                        .filter(i -> i.key().equals("privacy"))
                        .findFirst()
                        .orElseThrow()
                        .applicability())
                .isEqualTo("UNKNOWN");
        assertThat(PreparationPolicy.condition("false", false, false)).isNull();
        assertThat(PreparationPolicy.condition("false", true, false)).isFalse();
        assertThat(PreparationPolicy.condition("true", true, true)).isNull();
    }

    @Test
    void 연결_보완_재첨부_확인과_조건_왕복변경_재검토() {
        service.update(owner, 1L, command(owner, "ATTACH", "plan", null, "처음 첨부", null));
        verify(files).requireFile(2L, 7L);
        assertThatThrownBy(() ->
                        service.update(security, 1L, command(security, "REVIEW", "plan", "NEEDS_SUPPLEMENT", "", null)))
                .isInstanceOf(DomainRuleViolationException.class);
        var result =
                service.update(security, 1L, command(security, "REVIEW", "plan", "NEEDS_SUPPLEMENT", "범위 보완", null));
        assertThat(result.items().getFirst().status()).isEqualTo("NEEDS_SUPPLEMENT");
        service.update(owner, 1L, command(owner, "ATTACH", "plan", null, "보완 첨부", null));
        result = service.update(security, 1L, command(security, "REVIEW", "plan", "CONFIRMED", "확인", null));
        assertThat(result.items().getFirst().status()).isEqualTo("CONFIRMED");
        service.update(owner, 1L, command(owner, "CONDITIONS", null, null, null, Map.of("cloud", true)));
        result = service.update(owner, 1L, command(owner, "CONDITIONS", null, null, null, Map.of()));
        assertThat(result.items().getFirst().status()).isEqualTo("IN_REVIEW");
        assertThat(result.items().getFirst().stale()).isTrue();
    }

    @Test
    void 다른_분야와_신청자의_확인_미첨부_확인_오래된_저장_제출후_변경을_차단한다() {
        assertThatThrownBy(() -> service.update(infra, 1L, command(infra, "REVIEW", "plan", "CONFIRMED", "확인", null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.update(owner, 1L, command(owner, "REVIEW", "plan", "CONFIRMED", "확인", null)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() ->
                        service.update(security, 1L, command(security, "REVIEW", "plan", "CONFIRMED", "확인", null)))
                .isInstanceOf(DomainRuleViolationException.class);
        var old = command(owner, "ATTACH", "plan", null, "연결", null);
        service.update(owner, 1L, old);
        assertThatThrownBy(() -> service.update(owner, 1L, old)).isInstanceOf(ConflictException.class);
        when(request.isUnsubmitted()).thenReturn(false);
        assertThatThrownBy(() -> service.update(owner, 1L, command(owner, "ATTACH", "plan", null, "연결", null)))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
