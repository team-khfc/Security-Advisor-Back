package kr.securityadvisor.llm.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.llm.domain.InquiryMessage;

/**
 * 질의 메시지 응답. evidence 는 인용 태그(S·P)와 그 표기, flagged 는 근거 없는 내부 정보가 가려졌음을 뜻한다.
 */
public record InquiryMessageResponse(
        Long inquiryMessageId,
        Long inquiryId,
        InquiryMessage.Role role,
        String text,
        List<Evidence> evidence,
        String modelReference,
        boolean flagged,
        Instant createdAt) {

    /** 인용 한 건: tag(S12/P7)와 사람이 읽는 표기(출처·위치 / 이름). */
    public record Evidence(String tag, String label) {}

    public static InquiryMessageResponse from(InquiryMessage m, List<Evidence> evidence) {
        return new InquiryMessageResponse(
                m.getId(),
                m.getInquiryId(),
                m.getRole(),
                m.getText(),
                evidence,
                m.getModelReference(),
                m.isFlagged(),
                m.getCreatedAt());
    }
}
