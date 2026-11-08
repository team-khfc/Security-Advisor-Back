package kr.securityadvisor.notification.dto;

import jakarta.validation.constraints.NotNull;

/** 읽음 처리(PATCH /api/notifications/{id}). 업무 상태가 아니므로 PATCH 를 쓴다(AGENTS.md 5장). */
public record NotificationReadCommand(@NotNull Boolean read) {}
