package kr.securityadvisor.common.json;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** jsonb 컬럼(문자열 목록·속성 맵) 직렬화 공용 — 엔티티는 JSON 문자열만 들고, 변환은 서비스가 여기서 한다. */
@Component
@RequiredArgsConstructor
public class JsonCodec {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public String writeStrings(List<String> values) {
        return objectMapper.writeValueAsString(values == null ? List.of() : values);
    }

    public List<String> readStrings(String json) {
        return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, STRINGS);
    }

    public String writeStringMap(Map<String, String> values) {
        return objectMapper.writeValueAsString(values == null ? Map.of() : values);
    }

    public Map<String, String> readStringMap(String json) {
        return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, STRING_MAP);
    }

    public String write(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
