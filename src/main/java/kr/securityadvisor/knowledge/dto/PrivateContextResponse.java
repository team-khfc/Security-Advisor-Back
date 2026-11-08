package kr.securityadvisor.knowledge.dto;

import java.util.List;
import java.util.Map;
import kr.securityadvisor.knowledge.domain.PrivateContext;
import kr.securityadvisor.knowledge.domain.PrivateContextKind;

/** 비공개 컨텍스트 응답(권한 있는 역할에게만). */
public record PrivateContextResponse(
        Long privateContextId,
        PrivateContextKind kind,
        String name,
        List<String> aliases,
        Map<String, String> attributes,
        List<String> visibleRoles,
        String note,
        boolean active,
        long revision) {

    public static PrivateContextResponse from(
            PrivateContext c, List<String> aliases, Map<String, String> attributes, List<String> visibleRoles) {
        return new PrivateContextResponse(
                c.getId(),
                c.getKind(),
                c.getName(),
                aliases,
                attributes,
                visibleRoles,
                c.getNote(),
                c.isActive(),
                c.getRevision());
    }
}
