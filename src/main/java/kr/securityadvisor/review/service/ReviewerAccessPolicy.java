package kr.securityadvisor.review.service;

import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 담당자 검토 행위의 권한: 분야에 맞는 역할이면서 그 신청·분야의 활성 배정자여야 한다(FUNCTIONAL_SPEC 불변식 — 검토 행위는 배정자만).
 * 서버가 최종 판단하며 클라이언트의 role 값은 믿지 않는다(불변식 5). 조회는 여기서 막지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReviewerAccessPolicy {

    private final AssignmentService assignmentService;

    public void requireAssignedReviewer(MemberPrincipal principal, Long securityRequestId, ReviewField field) {
        if (!principal.hasRole(field.role())) {
            throw new ForbiddenException(field + " 분야 협의는 " + field.role() + " 역할만 처리할 수 있습니다");
        }
        if (!assignmentService.isActiveAssignee(securityRequestId, field, principal.username())) {
            throw new ForbiddenException("이 신청의 " + field + " 분야에 배정된 담당자만 처리할 수 있습니다");
        }
    }

    /** 신청 단위 행위(차수 반환·종결): 어느 분야든 활성 배정자이면 된다. */
    public ReviewField requireAnyAssignedReviewer(MemberPrincipal principal, Long securityRequestId) {
        for (ReviewField field : ReviewField.values()) {
            if (principal.hasRole(field.role())
                    && assignmentService.isActiveAssignee(securityRequestId, field, principal.username())) {
                return field;
            }
        }
        throw new ForbiddenException("이 신청에 배정된 담당자만 처리할 수 있습니다");
    }
}
