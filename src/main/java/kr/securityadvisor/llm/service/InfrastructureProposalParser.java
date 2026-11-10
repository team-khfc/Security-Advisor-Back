package kr.securityadvisor.llm.service;

import java.util.*;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.llm.dto.InfrastructureProposalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 모델이 붙인 인용을 실제 입력과 대조하고 근거 없는 필드는 비운다. */
@Component
@RequiredArgsConstructor
public class InfrastructureProposalParser {
    public static final List<String> KEYS = List.of(
            "sourceSystem",
            "sourceAddress",
            "sourceZone",
            "destinationSystem",
            "destinationAddress",
            "destinationZone",
            "protocol",
            "port",
            "purpose",
            "direction",
            "startDate",
            "endDate");
    private final ObjectMapper json;

    public List<InfrastructureProposalResponse.Connection> parse(
            String text, List<InquiryContextService.Source> sources) {
        try {
            if (text == null || text.length() > 100000) throw new IllegalArgumentException();
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            var root = json.readTree(text.substring(start, end + 1));
            var rows = root.path("connections");
            if (!rows.isArray() || rows.size() > 20) throw new IllegalArgumentException();
            Map<String, InquiryContextService.Source> refs = new HashMap<>();
            sources.forEach(s -> refs.put(s.tag(), s));
            List<InfrastructureProposalResponse.Connection> result = new ArrayList<>();
            for (var row : rows) {
                Map<String, InfrastructureProposalResponse.Field> fields = new LinkedHashMap<>();
                for (String key : KEYS) {
                    var f = row.path(key);
                    String value = f.path("value").asString("").strip();
                    String quote = f.path("quote").asString("").strip();
                    var source = refs.get(f.path("sourceTag").asString(""));
                    int limit = key.endsWith("System")
                            ? 200
                            : key.equals("purpose")
                                    ? 1000
                                    : key.equals("port") ? 500 : key.endsWith("Address") ? 4000 : 20;
                    boolean backed = source != null
                            && !value.isBlank()
                            && value.length() <= limit
                            && !quote.isBlank()
                            && quote.length() <= 2000
                            && normalize(source.text()).contains(normalize(quote))
                            && normalize(quote).contains(normalize(value));
                    if (backed && key.endsWith("Zone"))
                        backed = Set.of("외부", "DMZ", "내부망", "클라우드", "기타").contains(value);
                    if (backed && key.equals("direction"))
                        backed = Set.of("인바운드", "아웃바운드", "양방향").contains(value);
                    fields.put(
                            key,
                            backed
                                    ? new InfrastructureProposalResponse.Field(
                                            value, source.tag(), source.label(), quote)
                                    : new InfrastructureProposalResponse.Field("", null, null, null));
                }
                if (fields.values().stream().anyMatch(f -> !f.value().isBlank()))
                    result.add(new InfrastructureProposalResponse.Connection(Map.copyOf(fields)));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("AI 망 구성 응답을 읽지 못했습니다. 설명을 유지한 채 다시 시도하거나 직접 입력해 주세요.");
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }
}
