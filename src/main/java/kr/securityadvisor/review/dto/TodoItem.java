package kr.securityadvisor.review.dto;

/** 알림 읽음 여부와 무관하게 현재 업무 상태에서 계산한 작업 한 건. */
public record TodoItem(String key, String kind, Long securityRequestId, String title, String link) {}
