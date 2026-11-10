package kr.securityadvisor.llm.adapter;

import java.net.URI;

/** Spring AI 2의 SDK는 /v1을 자동 추가하지 않는다. 기존 seed 주소만 호환하며 사용자 경로는 유지한다. */
final class OpenAiEndpoint {
    private OpenAiEndpoint() {}

    static String baseUrl(String configured) {
        String value = configured.strip().replaceAll("/+$", "");
        URI uri = URI.create(value);
        String path = uri.getPath();
        if (path == null
                || path.isEmpty()
                || ("openrouter.ai".equalsIgnoreCase(uri.getHost()) && "/api".equals(path))) {
            return value + "/v1";
        }
        return value;
    }
}
