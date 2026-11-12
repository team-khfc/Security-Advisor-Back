package kr.securityadvisor.review;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.PreparationMaterialsResponse;
import kr.securityadvisor.request.repository.*;
import kr.securityadvisor.request.service.PreparationMaterialsService;
import kr.securityadvisor.review.repository.*;
import kr.securityadvisor.review.service.*;
import org.junit.jupiter.api.Test;

class PreparationTodoTest {
    @Test
    void 보완자료는_신청자_처리할일에_계산되고_재첨부와_제출후_사라진다() {
        var businesses = mock(BusinessCaseRepository.class);
        var requests = mock(SecurityRequestRepository.class);
        var materials = mock(PreparationMaterialsService.class);
        var request = mock(SecurityRequest.class);
        var business = mock(BusinessCase.class);
        var owner = new MemberPrincipal("owner", "합성 신청자", MemberRole.APPLICANT);
        when(business.getId()).thenReturn(2L);
        when(businesses.findByApplicantUsernameOrderByIdAsc("owner")).thenReturn(List.of(business));
        when(requests.findByBusinessCaseIdOrderByIdAsc(2L)).thenReturn(List.of(request));
        when(request.getId()).thenReturn(1L);
        when(request.getStage()).thenReturn(3);
        when(request.isUnsubmitted()).thenReturn(true);
        var service = new TodoService(
                mock(AssignmentService.class),
                List.of(),
                mock(ReviewRepository.class),
                mock(SupplementRepository.class),
                mock(SubmissionRepository.class),
                mock(FinalConfirmationRepository.class),
                requests,
                businesses,
                mock(RequiredReviewFields.class),
                materials);
        when(materials.get(owner, 1L)).thenReturn(materials("NEEDS_SUPPLEMENT"));
        assertThat(service.summarize(owner).preparationSupplements()).isEqualTo(1);
        assertThat(service.summarize(owner).total()).isEqualTo(1);
        when(materials.get(owner, 1L)).thenReturn(materials("IN_REVIEW"));
        assertThat(service.summarize(owner).total()).isZero();
        when(materials.get(owner, 1L)).thenReturn(materials("NEEDS_SUPPLEMENT"));
        when(request.isUnsubmitted()).thenReturn(false);
        assertThat(service.summarize(owner).total()).isZero();
    }

    private PreparationMaterialsResponse materials(String status) {
        return new PreparationMaterialsResponse(
                1,
                "hash",
                Map.of(),
                true,
                true,
                List.of(new PreparationMaterialsResponse.PreparationItem(
                        "plan",
                        "사업계획서",
                        "SECURITY",
                        "",
                        "",
                        "APPLICABLE",
                        status,
                        List.of(7L),
                        "범위 보완",
                        "합성 담당자",
                        null,
                        false,
                        true,
                        false)));
    }
}
