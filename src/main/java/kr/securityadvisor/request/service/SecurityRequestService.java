package kr.securityadvisor.request.service;

import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.SecurityRequestResponse;
import kr.securityadvisor.request.dto.WithdrawRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 유스케이스 중 신청자 행위(조회·철회). 협의 요청·제출·확정처럼 review 컨텍스트의 사실이 필요한 전이는
 * review 의 애플리케이션 서비스가 이 컨텍스트의 도메인 메서드를 호출해 수행한다.
 */
@Service
@Transactional(readOnly = true)
public class SecurityRequestService {

    private final RequestAccessPolicy accessPolicy;
    private final AnswerService answerService;

    public SecurityRequestService(RequestAccessPolicy accessPolicy, AnswerService answerService) {
        this.accessPolicy = accessPolicy;
        this.answerService = answerService;
    }

    /** 신청 상세(서류별 답변 포함). */
    public SecurityRequestResponse get(MemberPrincipal principal, Long securityRequestId) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        return SecurityRequestResponse.from(request, answerService.requestAnswers(securityRequestId));
    }

    /** 신청자 철회(F-05). 사유 필수, 이후 조회만 가능. 같은 사업 건의 다른 신청은 바뀌지 않는다(불변식 16). */
    @Transactional
    public SecurityRequestResponse withdraw(MemberPrincipal principal, Long securityRequestId, WithdrawRequest request) {
        SecurityRequest securityRequest = accessPolicy.ownedRequest(principal, securityRequestId);
        securityRequest.requireRevision(request.expectedRevision());
        securityRequest.withdraw(request.reason());
        // TODO(정용석): audit 컨텍스트 도입 후 철회(사유 포함)를 audit_events 에 기록한다(F-14).
        return SecurityRequestResponse.from(securityRequest, answerService.requestAnswers(securityRequestId));
    }
}
