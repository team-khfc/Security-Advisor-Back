package kr.securityadvisor.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.knowledge.domain.PrivateContextKind;

/** 비공개 컨텍스트 생성·수정(관리자). visibleRoles 에 APPLICANT 는 허용되지 않는다. 값은 전부 합성 데이터여야 한다. */
public record PrivateContextCommand(
        @NotNull PrivateContextKind kind,
        @NotBlank @Size(max = 200) String name,
        List<String> aliases,
        @NotNull Map<String, String> attributes,
        @NotEmpty List<String> visibleRoles,
        @Size(max = 2000) String note,
        Boolean active,
        Long expectedRevision) {}
