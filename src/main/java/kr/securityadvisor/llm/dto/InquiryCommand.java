package kr.securityadvisor.llm.dto;

import jakarta.validation.constraints.Size;

/** 질의 대화 생성(POST /api/security-requests/{id}/inquiries). 분야는 호출자 역할로 정해진다. */
public record InquiryCommand(@Size(max = 200) String title) {}
