package kr.securityadvisor.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeDraftCommand(
        @NotNull Long expectedRevision, @Size(max = 500) String note) {}
