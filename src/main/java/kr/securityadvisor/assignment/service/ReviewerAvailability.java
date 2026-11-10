package kr.securityadvisor.assignment.service;

import kr.securityadvisor.assignment.repository.ReviewerPoolRepository;
import kr.securityadvisor.auth.service.MemberDirectory;
import kr.securityadvisor.common.schema.ReassignmentReason;
import kr.securityadvisor.common.schema.ReviewField;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신규 배정 후보와 기존 배정의 인계 필요 여부에 같은 계정·분야 등록 기준을 적용한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewerAvailability {
    private final MemberDirectory memberDirectory;
    private final ReviewerPoolRepository poolRepository;

    /** null이면 적격 담당자이며, 그 외에는 기존 배정을 보존한 채 재배정 사유를 반환한다. */
    public ReassignmentReason reason(String username, ReviewField field) {
        if (username == null) return ReassignmentReason.UNASSIGNED;
        ReassignmentReason account = memberDirectory.reviewerUnavailableReason(username, field.role());
        if (account != null) return account;
        var pool = poolRepository.findByFieldAndMemberUsername(field, username).orElse(null);
        if (pool == null) return ReassignmentReason.NOT_REGISTERED;
        return pool.isActive() ? null : ReassignmentReason.POOL_INACTIVE;
    }
}
