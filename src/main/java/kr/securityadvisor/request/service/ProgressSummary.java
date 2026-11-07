package kr.securityadvisor.request.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;

/** 사업 건 진행 상태를 하위 신청에서 계산한다(불변식 15). 예: "2건 중 1건 확정, 1건 보완 요청". */
final class ProgressSummary {

    private ProgressSummary() {}

    static String of(List<SecurityRequest> requests) {
        if (requests.isEmpty()) {
            return "신청 없음";
        }
        Map<RequestStatus, Long> counts = requests.stream()
                .collect(Collectors.groupingBy(SecurityRequest::getStatus, LinkedHashMap::new, Collectors.counting()));
        String detail = counts.entrySet().stream()
                .map(entry -> entry.getValue() + "건 " + entry.getKey().label())
                .collect(Collectors.joining(", "));
        return requests.size() + "건 중 " + detail;
    }
}
