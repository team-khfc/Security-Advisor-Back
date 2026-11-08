package kr.securityadvisor.configuration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.securityadvisor.common.schema.FieldSchema;

/**
 * 초안 저장(PUT /api/configurations/{id}). 전체 스키마를 교체한다(저장 키 변경은 문구 수정과 다른 데이터 계약 변경이므로 검증 대상).
 */
public record ConfigurationDraftCommand(
        @NotNull FieldSchema draft,
        @Size(max = 500) String note,
        @NotNull Long expectedRevision) {}
