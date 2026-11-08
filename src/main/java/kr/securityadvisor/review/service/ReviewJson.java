package kr.securityadvisor.review.service;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 협의 컨텍스트의 jsonb 컬럼(체크리스트·대상 항목 키) 직렬화 — 서비스 밖으로 JSON 문자열이 새지 않게 한곳에 모은다. */
@Component
@RequiredArgsConstructor
public class ReviewJson {

    private static final TypeReference<Map<String, Boolean>> CHECKLIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> REASONS = new TypeReference<>() {};
    private static final TypeReference<List<String>> KEYS = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String writeChecklist(Map<String, Boolean> checklist) {
        return objectMapper.writeValueAsString(checklist == null ? Map.of() : checklist);
    }

    public Map<String, Boolean> readChecklist(String json) {
        return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, CHECKLIST);
    }

    public String writeKeys(List<String> keys) {
        return objectMapper.writeValueAsString(keys == null ? List.of() : keys);
    }

    public List<String> readKeys(String json) {
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, KEYS);
    }

    public String writeReasons(Map<String, String> reasons) {
        return objectMapper.writeValueAsString(reasons == null ? Map.of() : reasons);
    }

    public Map<String, String> readReasons(String json) {
        return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, REASONS);
    }
}
