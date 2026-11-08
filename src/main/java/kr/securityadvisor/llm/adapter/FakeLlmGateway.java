package kr.securityadvisor.llm.adapter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.DocTypeSuggestionService;
import kr.securityadvisor.llm.service.LlmBackend;
import kr.securityadvisor.llm.service.LlmPrompt;
import kr.securityadvisor.llm.service.LlmResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * fake 모드: 네트워크 없이 결정적 응답을 돌려준다(테스트·오프라인 시연). 실제 모델 품질을 대신하지 않는다.
 * INTERVIEW: 사용자 프롬프트에 나온 항목 키마다 합성 제안을 만들고 첫 지식 조각을 근거로 단다. 지식이 없으면 제안하지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "securityadvisor.llm", name = "mode", havingValue = "fake")
public class FakeLlmGateway implements LlmBackend {

    static final String REFERENCE = "fake/offline";

    private static final Pattern FIELD_KEY = Pattern.compile("\"([a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*)\"");
    private static final Pattern SNIPPET_TAG = Pattern.compile("\\[(S\\d+)]");
    private static final Pattern DOC_TYPE_CODE = Pattern.compile("\\{\"code\":\"([A-Z][A-Z0-9_]*)\"");

    @Override
    public LlmResult complete(LlmPurpose purpose, LlmPrompt prompt) {
        return switch (purpose) {
            case INTERVIEW ->
                new LlmResult(
                        prompt.system().contains(DocTypeSuggestionService.MARKER)
                                ? docTypeSuggestions(prompt)
                                : proposals(prompt),
                        REFERENCE);
            case SUMMARY -> new LlmResult("합성 요약: 입력 문서의 핵심을 요약한 것으로 가정한 고정 응답입니다.", REFERENCE);
            case DOCUMENT -> new LlmResult("합성 초안: 확정된 값으로 채운 서류 초안으로 가정한 고정 응답입니다.", REFERENCE);
            case INQUIRY -> new LlmResult(inquiry(prompt), REFERENCE);
        };
    }

    /** 목록의 모든 코드를 "합성 근거"와 함께 제안한다(첫 번째는 HIGH, 나머지 MEDIUM). */
    private static String docTypeSuggestions(LlmPrompt prompt) {
        Matcher m = DOC_TYPE_CODE.matcher(prompt.user());
        StringBuilder sb = new StringBuilder("{\"suggestions\":[");
        boolean first = true;
        while (m.find()) {
            if (!first) {
                sb.append(',');
            }
            sb.append("{\"docType\":\"")
                    .append(m.group(1))
                    .append("\",\"reason\":\"합성 근거: 개요에 외부 연동·신규 서비스가 언급됨\",\"confidence\":\"")
                    .append(first ? "HIGH" : "MEDIUM")
                    .append("\"}");
            first = false;
        }
        return sb.append("]}").toString();
    }

    private static String proposals(LlmPrompt prompt) {
        String firstTag = prompt.knowledgeLines().stream()
                .map(SNIPPET_TAG::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .findFirst()
                .orElse(null);
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = FIELD_KEY.matcher(prompt.user());
        while (m.find()) {
            keys.add(m.group(1));
        }
        StringBuilder sb = new StringBuilder("{\"proposals\":[");
        boolean first = true;
        for (String key : keys) {
            if (firstTag == null) {
                break;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"fieldKey\":\"")
                    .append(key)
                    .append("\",\"value\":\"합성 제안(")
                    .append(key)
                    .append(")\",\"evidence\":[\"")
                    .append(firstTag)
                    .append("\"]}");
        }
        return sb.append("]}").toString();
    }

    private static String inquiry(LlmPrompt prompt) {
        String facts = prompt.factLines().isEmpty()
                ? ""
                : " 참고한 기관 사실: " + prompt.factLines().getFirst();
        String knowledge = prompt.knowledgeLines().isEmpty()
                ? ""
                : " 근거: " + prompt.knowledgeLines().getFirst();
        return "{\"answer\":\"합성 답변입니다." + facts.replace("\"", "'") + knowledge.replace("\"", "'")
                + "\",\"evidence\":[]}";
    }
}
