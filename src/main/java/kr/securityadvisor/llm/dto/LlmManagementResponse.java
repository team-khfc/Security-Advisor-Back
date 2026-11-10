package kr.securityadvisor.llm.dto;

import java.util.List;
import kr.securityadvisor.llm.domain.LlmPurpose;

/** 실제 호출 모드와 용도별 선택 결과. 키 값은 제공하지 않는다. */
public record LlmManagementResponse(
        String mode, List<String> allowedHosts, List<KeyReference> keyReferences, List<Route> routes) {
    public record KeyReference(String name, boolean configured) {}

    public record Route(LlmPurpose purpose, Long configurationId, String modelReference, String status) {}
}
