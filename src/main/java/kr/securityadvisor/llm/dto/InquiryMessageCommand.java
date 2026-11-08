package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 질문 보내기(POST /api/inquiries/{id}/messages). 비밀값·개인정보를 넣지 않는다. */
public record InquiryMessageCommand(
        @NotBlank @Size(max = 4000) String text) {}
