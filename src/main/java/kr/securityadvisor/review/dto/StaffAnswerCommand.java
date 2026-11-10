package kr.securityadvisor.review.dto;

import jakarta.validation.constraints.Size;

/**
 * 담당자 입력(STAFF_INPUT) 요청.
 *
 * @param value 값(예: 할당한 서버·IP·포트)
 * @param expectedRevision 값이 있던 답변을 고칠 때 그 revision(불일치 409). "담당자 확인 필요" 자리를 처음 채울 때는 생략 가능
 */
public record StaffAnswerCommand(@Size(max = 4000) String value, Long expectedRevision, Boolean notApplicable) {
    @com.fasterxml.jackson.annotation.JsonCreator
    public StaffAnswerCommand {
        notApplicable = Boolean.TRUE.equals(notApplicable);
    }

    public StaffAnswerCommand(String value, Long expectedRevision) {
        this(value, expectedRevision, false);
    }
}
