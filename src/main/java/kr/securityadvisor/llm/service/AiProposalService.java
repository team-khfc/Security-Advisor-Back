package kr.securityadvisor.llm.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.knowledge.service.KnowledgeSearch;
import kr.securityadvisor.knowledge.service.SnippetHit;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.AiProposalCommand;
import kr.securityadvisor.llm.dto.AiProposalResult;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * F-03 AI 제안: 신청자 항목 중 값이 없거나 미확정 AI 값인 것에 대해, 사용자가 이미 적은 내용과 공개 지식을 근거로 값을 제안한다.
 * 제안은 AI_INFERRED·미확정으로 저장되며(원칙 1·2), 근거(조각 ID)가 없는 제안과 내부 정보처럼 보이는 값은 버린다(원칙 3).
 * 담당자 항목(askRole≠APPLICANT)은 제안 대상이 아니다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AiProposalService {

    private static final String SYSTEM = """
            너는 공공기관 정보보안 서류 작성을 돕는 보조자다. 아래 규칙을 지켜라.
            1. 너는 제안만 한다. 확정은 사람이 한다. 확신이 없는 항목은 제안하지 않는다.
            2. 제안의 근거는 [공개 지식] 조각에서만 인용한다. 근거가 없는 항목은 제안하지 않는다.
            3. 기관 내부 정보(서버명·IP·대역·자산)는 어떤 경우에도 추측하거나 만들어내지 않는다.
            4. 사용자가 이미 적은 내용과 모순되는 값을 제안하지 않는다.
            5. 출력은 JSON 하나만: {"proposals":[{"fieldKey":"...","value":"...","evidence":["S12"]}]} — 다른 글자는 쓰지 않는다.
            """;

    private final RequestAccessPolicy accessPolicy;
    private final PublicationService publicationService;
    private final AnswerService answerService;
    private final KnowledgeSearch knowledgeSearch;
    private final LlmGateway llmGateway;
    private final InternalInfoGuard guard;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public AiProposalResult propose(MemberPrincipal principal, Long securityRequestId, AiProposalCommand command) {
        SecurityRequest request = accessPolicy.ownedRequest(principal, securityRequestId);
        if (!request.isUnsubmitted()) {
            throw new DomainRuleViolationException("제출·종결·철회된 신청에는 AI 제안을 넣을 수 없습니다");
        }
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());

        Map<String, AnswerResponse> answers = answerService.allAnswers(request).stream()
                .collect(Collectors.toMap(AnswerResponse::fieldKey, a -> a, (a, b) -> b, LinkedHashMap::new));
        Set<String> requested = command == null || command.fieldKeys() == null
                ? Set.of()
                : command.fieldKeys().stream().collect(Collectors.toSet());
        List<FieldDefinition> candidates = candidateFields(schema, request, answers, requested);
        List<AiProposalResult.Skipped> skipped = new ArrayList<>();
        if (candidates.isEmpty()) {
            return new AiProposalResult(null, List.of(), List.of(new AiProposalResult.Skipped("*", "제안할 항목이 없습니다")));
        }

        // 컨텍스트: 사용자가 적은 값(담당자 항목·내부 정보는 신청자에게도 보이는 값만이지만 프롬프트에는 신청자 항목만 넣는다)
        String written = answers.values().stream()
                .filter(a -> a.value() != null && !a.value().isBlank() && a.valueSource() == ValueSource.USER_ANSWER)
                .map(a -> "- " + a.fieldKey() + ": " + a.value())
                .collect(Collectors.joining("\n"));
        Map<String, SnippetHit> hits = new LinkedHashMap<>();
        for (FieldDefinition field : candidates) {
            knowledgeSearch
                    .forField(field.key(), request.getDocType(), written)
                    .forEach(h -> hits.putIfAbsent(h.tag(), h));
        }
        List<String> knowledgeLines = hits.values().stream()
                .map(h -> "[" + h.tag() + "] " + h.citation() + " — " + h.body())
                .toList();
        String user = "서류 유형: " + request.getDocType().code() + "\n\n사용자가 이미 적은 내용:\n"
                + (written.isBlank() ? "(없음)" : written) + "\n\n제안할 항목(JSON 배열):\n"
                + objectMapper.writeValueAsString(candidates.stream()
                        .map(f -> Map.of(
                                "fieldKey",
                                f.key(),
                                "label",
                                f.label(),
                                "type",
                                f.type(),
                                "hint",
                                f.hint() == null ? "" : f.hint()))
                        .toList());

        LlmResult result =
                llmGateway.complete(LlmPurpose.INTERVIEW, LlmPrompt.of(SYSTEM, knowledgeLines, List.of(), user));
        List<AnswerResponse> proposed = new ArrayList<>();
        Set<String> candidateKeys =
                candidates.stream().map(FieldDefinition::key).collect(Collectors.toSet());
        for (JsonNode node : parseProposals(result.text())) {
            String fieldKey = node.path("fieldKey").asString("");
            String value = node.path("value").asString("");
            List<String> evidence = new ArrayList<>();
            node.path("evidence").forEach(e -> evidence.add(e.asString("")));
            String reason = reject(fieldKey, value, evidence, candidateKeys, hits);
            if (reason != null) {
                skipped.add(new AiProposalResult.Skipped(fieldKey.isBlank() ? "?" : fieldKey, reason));
                auditRecorder.record(
                        principal,
                        AuditTargetType.SECURITY_REQUEST,
                        String.valueOf(securityRequestId),
                        request.getRevision(),
                        AuditAction.AI_PROPOSAL_REJECTED,
                        reason,
                        "AI 제안 거부 " + fieldKey + " (" + result.modelReference() + ")");
                continue;
            }
            String evidenceReference = evidence.stream()
                    .map(tag -> hits.get(tag).citation() + " (" + tag + ")")
                    .collect(Collectors.joining("; "));
            try {
                proposed.add(answerService.proposeByAi(
                        principal, request, fieldKey, value, evidenceReference, result.modelReference()));
            } catch (DomainRuleViolationException e) {
                skipped.add(new AiProposalResult.Skipped(fieldKey, e.getMessage()));
            }
        }
        candidateKeys.stream()
                .filter(key -> proposed.stream().noneMatch(p -> p.fieldKey().equals(key))
                        && skipped.stream().noneMatch(s -> s.fieldKey().equals(key)))
                .forEach(key -> skipped.add(new AiProposalResult.Skipped(key, "모델이 근거 있는 제안을 내지 않았습니다")));
        return new AiProposalResult(result.modelReference(), proposed, skipped);
    }

    /** 신청자 항목 중 값이 없거나 미확정 AI 값인 것(확인된 값은 건드리지 않는다). */
    private static List<FieldDefinition> candidateFields(
            FieldSchema schema, SecurityRequest request, Map<String, AnswerResponse> answers, Set<String> requested) {
        List<FieldDefinition> fields = new ArrayList<>(schema.caseFields());
        fields.addAll(schema.requestFields(request.getDocType()));
        return fields.stream()
                .filter(f -> !f.isStaffField())
                .filter(f -> requested.isEmpty() || requested.contains(f.key()))
                .filter(f -> {
                    AnswerResponse a = answers.get(f.key());
                    return a == null
                            || a.value() == null
                            || a.value().isBlank()
                            || (a.valueSource() == ValueSource.AI_INFERRED && !a.confirmed());
                })
                .toList();
    }

    private String reject(
            String fieldKey,
            String value,
            List<String> evidence,
            Set<String> candidateKeys,
            Map<String, SnippetHit> hits) {
        if (!candidateKeys.contains(fieldKey)) {
            return "제안 대상이 아닌 항목";
        }
        if (value.isBlank()) {
            return "값이 비어 있음";
        }
        if (evidence.isEmpty() || !evidence.stream().allMatch(hits::containsKey)) {
            return "제공된 지식 조각을 근거로 인용하지 않음";
        }
        List<String> unbacked = guard.findUnbacked(value, List.of());
        if (!unbacked.isEmpty()) {
            return "근거 없는 내부 정보(IP·대역) 포함";
        }
        return null;
    }

    private List<JsonNode> parseProposals(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
            List<JsonNode> list = new ArrayList<>();
            root.path("proposals").forEach(list::add);
            return list;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
