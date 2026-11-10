package kr.securityadvisor.configuration.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.configuration.domain.Configuration;

/** 설정 초안 응답. currentPublishedVersion 과 basedOnVersion 이 다르면 다른 곳에서 먼저 게시된 것. */
public record ConfigurationResponse(
        Long configurationId,
        FieldSchema draft,
        int basedOnVersion,
        int currentPublishedVersion,
        String note,
        String updatedBy,
        Instant updatedAt,
        long revision,
        List<FieldDefinition> builtInFields) {

    public static ConfigurationResponse from(
            Configuration c, FieldSchema draft, int currentPublishedVersion, List<FieldDefinition> builtInFields) {
        return new ConfigurationResponse(
                c.getId(),
                draft,
                c.getBasedOnVersion(),
                currentPublishedVersion,
                c.getNote(),
                c.getUpdatedBy(),
                c.getUpdatedAt(),
                c.getRevision(),
                builtInFields);
    }
}
