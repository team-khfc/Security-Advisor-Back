package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;

public record ConsistencyReviewResponse(
        Long id,
        long revision,
        boolean stale,
        boolean canDecide,
        List<Integer> allowedFindings,
        ConsistencyCheckResponse result,
        List<Decision> decisions) {
    public record Decision(
            int findingIndex, String status, String reason, String actor, Instant at, Long supplementId) {}
}
