package kr.securityadvisor.knowledge.service;

import java.util.Map;
import kr.securityadvisor.knowledge.domain.PrivateContextKind;

/**
 * 프롬프트에 "사실" 블록으로 들어가는 비공개 컨텍스트 한 행. 모델은 {@code [P<id>]} 로 인용한다.
 */
public record FactRecord(Long privateContextId, PrivateContextKind kind, String name, Map<String, String> attributes) {

    public String tag() {
        return "P" + privateContextId;
    }

    /** 한 줄 표기: name | key=value | … */
    public String line() {
        StringBuilder sb = new StringBuilder(name);
        attributes.forEach((k, v) -> sb.append(" | ").append(k).append('=').append(v));
        return sb.toString();
    }
}
