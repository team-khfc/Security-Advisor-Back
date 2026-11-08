package kr.securityadvisor.configuration.dto;

import jakarta.validation.constraints.NotNull;

/** 게시(POST /api/configurations/{id}/publications). 초안의 revision 으로 충돌을 막는다. */
public record PublishCommand(@NotNull Long expectedRevision) {}
