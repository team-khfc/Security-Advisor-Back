package kr.securityadvisor.llm.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.llm.domain.AnalysisJob;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.DocTypeSuggestionCommand;
import kr.securityadvisor.llm.dto.DocTypeSuggestionResult;
import kr.securityadvisor.llm.repository.AnalysisJobRepository;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 서류 유형 제안(F-01): "어떤 서류를 내야 하는지 모르는" 신청자에게 게시본의 활성 서류 유형 중 필요한 것을 근거와 함께 제안한다.
 * 제안만 하고 선택은 사용자가 한다. 게시본에 없는 유형·근거 없는 제안은 버린다. 내부 정보는 프롬프트에 넣지 않는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocTypeSuggestionService {

    /** fake 백엔드가 이 토큰으로 제안 프롬프트를 식별한다. */
    public static final String MARKER = "DOC_TYPE_SUGGESTION";

    private static final String SYSTEM = """
            너는 공공기관 정보보안 절차 안내자다(""" + MARKER + """
            ). 사용자가 적은 사업 개요와 문서 요약을 읽고, 아래 [서류 유형 목록] 중에서 이 사업에 필요한 서류를 고른다.
            규칙: 목록에 없는 유형은 절대 만들지 않는다. 필요 여부가 불확실하면 confidence 를 LOW 로 적는다. 근거(reason)는 개요의
            어떤 내용 때문인지 한두 문장으로 쓴다. 기관 내부 정보는 추측하지 않는다.
            출력은 JSON 하나만: {"suggestions":[{"docType":"코드","reason":"...","confidence":"HIGH|MEDIUM|LOW"}]}
            """;

    private final PublicationService publicationService;
    private final RequestAccessPolicy accessPolicy;
    private final AnalysisJobRepository analysisJobRepository;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;

    public DocTypeSuggestionResult suggest(MemberPrincipal principal, DocTypeSuggestionCommand command) {
        StringBuilder context = new StringBuilder();
        Long publicationId;
        if (command.businessCaseId() != null) {
            BusinessCase businessCase = accessPolicy.viewableCase(principal, command.businessCaseId());
            publicationId = businessCase.getPublicationId();
            context.append("사업명: ").append(businessCase.getTitle()).append('\n');
            if (businessCase.getSummary() != null) {
                context.append("개요: ").append(businessCase.getSummary()).append('\n');
            }
            for (AnalysisJob job : analysisJobRepository.findByBusinessCaseIdAndStatusOrderByIdDesc(
                    businessCase.getId(), AnalysisStatus.NEEDS_REVIEW)) {
                if (job.getSummary() != null) {
                    context.append("문서 AI 요약(미확정): ").append(job.getSummary()).append('\n');
                }
            }
        } else {
            publicationId = publicationService.currentPublicationId();
        }
        if (command.summary() != null && !command.summary().isBlank()) {
            context.append("사용자 입력 개요: ").append(command.summary().strip()).append('\n');
        }
        if (context.isEmpty()) {
            throw new BadRequestException("사업 개요(summary) 또는 사업 건(businessCaseId)이 필요합니다");
        }

        FieldSchema schema = publicationService.getFieldSchema(publicationId);
        List<DocTypeDefinition> active = schema.activeDocTypes();
        StringBuilder user = new StringBuilder("[서류 유형 목록]\n");
        for (DocTypeDefinition d : active) {
            user.append("{\"code\":\"")
                    .append(d.code().code())
                    .append("\",\"name\":\"")
                    .append(d.name())
                    .append("\",\"description\":\"")
                    .append(d.description() == null ? "" : d.description())
                    .append("\"}\n");
        }
        user.append("\n[사업 내용]\n").append(context);

        LlmResult result = llmGateway.complete(
                LlmPurpose.INTERVIEW,
                LlmPrompt.of(SYSTEM, List.of(), List.of(), user.toString()),
                new LlmCallContext(
                        principal,
                        command.businessCaseId() == null
                                ? AuditTargetType.LLM_CONFIGURATION
                                : AuditTargetType.BUSINESS_CASE,
                        command.businessCaseId() == null
                                ? "doc-type-suggestion"
                                : String.valueOf(command.businessCaseId())));

        Set<String> seen = new LinkedHashSet<>();
        List<DocTypeSuggestionResult.Suggestion> suggestions = new ArrayList<>();
        for (JsonNode node : parse(result.text())) {
            String code = node.path("docType").asString("").strip().toUpperCase(Locale.ROOT);
            String reason = node.path("reason").asString("").strip();
            String confidence = node.path("confidence").asString("LOW").strip().toUpperCase(Locale.ROOT);
            DocTypeDefinition definition = active.stream()
                    .filter(d -> d.code().code().equals(code))
                    .findFirst()
                    .orElse(null);
            if (definition == null || reason.isEmpty() || !seen.add(code)) {
                continue; // 목록에 없는 유형·근거 없는 제안·중복은 버린다
            }
            suggestions.add(new DocTypeSuggestionResult.Suggestion(
                    code,
                    definition.name(),
                    definition.description(),
                    reason,
                    Set.of("HIGH", "MEDIUM", "LOW").contains(confidence) ? confidence : "LOW"));
        }
        List<DocTypeSuggestionResult.DocTypeOption> rest = active.stream()
                .filter(d -> !seen.contains(d.code().code()))
                .map(d -> new DocTypeSuggestionResult.DocTypeOption(d.code().code(), d.name(), d.description()))
                .toList();
        return new DocTypeSuggestionResult(
                suggestions, rest, result.modelReference(), "AI 제안입니다. 필요한 서류는 신청자가 직접 선택·확정합니다.");
    }

    private List<JsonNode> parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            List<JsonNode> items = new ArrayList<>();
            root.path("suggestions").forEach(items::add);
            return items;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
