package kr.securityadvisor.llm.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.llm.domain.Inquiry;

/** 질의 대화 응답. */
public record InquiryResponse(
        Long inquiryId,
        Long securityRequestId,
        String staffUsername,
        ReviewField field,
        String title,
        Instant createdAt) {

    public static InquiryResponse from(Inquiry inquiry) {
        return new InquiryResponse(
                inquiry.getId(),
                inquiry.getSecurityRequestId(),
                inquiry.getStaffUsername(),
                inquiry.getField(),
                inquiry.getTitle(),
                inquiry.getCreatedAt());
    }
}
