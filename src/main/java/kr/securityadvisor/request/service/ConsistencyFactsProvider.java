package kr.securityadvisor.request.service;

import java.util.List;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.ConsistencyReviewResponse;

/** 제출은 AI 구현에 의존하지 않고 기록 제공 포트로 검사 당시 사실을 받는다. */
public interface ConsistencyFactsProvider {
    List<ConsistencyReviewResponse> snapshot(MemberPrincipal actor, Long requestId);
}
