package kr.securityadvisor.knowledge.service;

import java.util.List;

/**
 * 검색된 지식 조각. 프롬프트에는 {@code [S<id>] citation} 형태로 들어가고, 모델은 그 ID 로 근거를 인용한다.
 *
 * @param citation 출처 제목·판본·위치
 */
public record SnippetHit(Long snippetId, String citation, String body, List<String> fieldKeys) {

    public String tag() {
        return "S" + snippetId;
    }
}
