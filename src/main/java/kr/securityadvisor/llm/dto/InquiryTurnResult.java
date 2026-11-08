package kr.securityadvisor.llm.dto;

/** 한 턴의 결과: 저장된 질문과 답변. */
public record InquiryTurnResult(InquiryMessageResponse question, InquiryMessageResponse answer) {}
