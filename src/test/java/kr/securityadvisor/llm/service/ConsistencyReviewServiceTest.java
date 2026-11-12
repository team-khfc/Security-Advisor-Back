package kr.securityadvisor.llm.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.schema.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.llm.domain.ConsistencyReview;
import kr.securityadvisor.llm.dto.ConsistencyDecisionCommand;
import kr.securityadvisor.llm.repository.ConsistencyReviewRepository;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.ConsistencyCheckResponse;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.dto.*;
import kr.securityadvisor.review.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConsistencyReviewServiceTest {
    final MemberPrincipal staff = new MemberPrincipal("synthetic", "합성 담당자", MemberRole.SECURITY);
    final ConsistencyReviewRepository repository = mock(ConsistencyReviewRepository.class);
    final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    final ConsistencyCheckService checks = mock(ConsistencyCheckService.class);
    final AssignmentService assignments = mock(AssignmentService.class);
    final ReviewService reviews = mock(ReviewService.class);
    final SupplementService supplements = mock(SupplementService.class);
    final PublicationService publications = mock(PublicationService.class);
    final ObjectMapper json = new ObjectMapper();
    final ConsistencyReviewService service = new ConsistencyReviewService(
            repository,
            access,
            checks,
            assignments,
            reviews,
            supplements,
            mock(AuditRecorder.class),
            json,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            mock(EntityManager.class),
            publications);
    final ConsistencyReview row;

    ConsistencyReviewServiceTest() {
        var request = mock(SecurityRequest.class);
        when(request.getId()).thenReturn(1L);
        when(request.getBusinessCaseId()).thenReturn(2L);
        when(request.getStage()).thenReturn(2);
        when(request.isUnsubmitted()).thenReturn(true);
        when(access.viewableRequest(any(), eq(1L))).thenReturn(request);
        var business = mock(BusinessCase.class);
        when(business.getPublicationId()).thenReturn(3L);
        when(access.viewableCase(any(), eq(2L))).thenReturn(business);
        when(publications.getFieldSchema(3L))
                .thenReturn(new FieldSchema(
                        null,
                        List.of(),
                        List.of(new FieldDefinition(
                                "data.personal_info",
                                "개인정보",
                                AnswerScope.CASE,
                                Set.of(),
                                "BOOLEAN",
                                true,
                                MemberRole.APPLICANT,
                                null))));
        when(assignments.isActiveAssignee(1L, ReviewField.SECURITY, "synthetic"))
                .thenReturn(true);
        when(checks.fingerprint(any(), eq(1L), any())).thenReturn("same");
        var result = new ConsistencyCheckResponse(
                "same",
                Instant.EPOCH,
                "synthetic",
                1,
                1,
                List.of(new ConsistencyCheckResponse.Source(4L, "합성.txt", false, false)),
                List.of(new ConsistencyCheckResponse.Finding(
                        "data.personal_info", "개인정보", "false", true, 4L, "성명을 수집합니다", "처리 여부 확인")));
        row = ConsistencyReview.create(1L, json.writeValueAsString(result));
        when(repository.findById(5L)).thenReturn(Optional.of(row));
    }

    ConsistencyDecisionCommand command(String status, long revision) {
        return new ConsistencyDecisionCommand(revision, "same", 0, status, "합성 판단 사유");
    }

    @Test
    void 처리_이력을_덮어쓰지_않고_판단과_재개를_보존한다() {
        service.decide(staff, 1L, 5L, command("DISMISSED", 0));
        var result = service.decide(staff, 1L, 5L, command("OPEN", 0));
        assertThat(result.decisions()).extracting(d -> d.status()).containsExactly("DISMISSED", "OPEN");
        assertThatThrownBy(() -> service.decide(staff, 1L, 5L, command("RESOLVED", 9)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void 입력이_바뀐_검사와_신청자의_확정은_차단한다() {
        when(checks.fingerprint(any(), eq(1L), any())).thenReturn("changed");
        assertThatThrownBy(() -> service.decide(staff, 1L, 5L, command("RESOLVED", 0)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.decide(
                        new MemberPrincipal("owner", "합성 신청자", MemberRole.APPLICANT), 1L, 5L, command("RESOLVED", 0)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(supplements);
    }

    @Test
    void 보완_요청은_기존_업무에_연결하고_식별자를_보존한다() {
        var review = mock(ReviewResponse.class);
        when(review.field()).thenReturn(ReviewField.SECURITY);
        when(review.reviewId()).thenReturn(6L);
        when(reviews.listForRequest(staff, 1L)).thenReturn(List.of(review));
        var supplement = mock(SupplementResponse.class);
        when(supplement.supplementId()).thenReturn(7L);
        when(supplements.request(eq(staff), eq(6L), any())).thenReturn(supplement);
        assertThat(service.decide(staff, 1L, 5L, command("SUPPLEMENT_REQUESTED", 0))
                        .decisions()
                        .getFirst()
                        .supplementId())
                .isEqualTo(7L);
        verify(supplements)
                .request(eq(staff), eq(6L), argThat(c -> c.fieldKeys().equals(List.of("data.personal_info"))));
    }
}
