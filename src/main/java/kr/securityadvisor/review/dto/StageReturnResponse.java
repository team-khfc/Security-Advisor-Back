package kr.securityadvisor.review.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.review.domain.StageReturn;

/** 차수 반환 기록 응답. */
public record StageReturnResponse(
        Long stageReturnId,
        Long securityRequestId,
        int fromStage,
        int toStage,
        String reason,
        List<String> fieldKeys,
        String returnedBy,
        Instant returnedAt) {

    public static StageReturnResponse from(StageReturn stageReturn, List<String> fieldKeys) {
        return new StageReturnResponse(
                stageReturn.getId(),
                stageReturn.getSecurityRequestId(),
                stageReturn.getFromStage(),
                stageReturn.getToStage(),
                stageReturn.getReason(),
                fieldKeys,
                stageReturn.getReturnedBy(),
                stageReturn.getReturnedAt());
    }
}
