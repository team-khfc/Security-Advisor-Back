package kr.securityadvisor.llm.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.service.FactRecord;
import kr.securityadvisor.knowledge.service.KnowledgeSearch;
import kr.securityadvisor.knowledge.service.PrivateContextLookup;
import kr.securityadvisor.knowledge.service.SnippetHit;
import kr.securityadvisor.llm.domain.Inquiry;
import kr.securityadvisor.llm.domain.InquiryMessage;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.InquiryCommand;
import kr.securityadvisor.llm.dto.InquiryMessageCommand;
import kr.securityadvisor.llm.dto.InquiryMessageResponse;
import kr.securityadvisor.llm.dto.InquiryResponse;
import kr.securityadvisor.llm.dto.InquiryTurnResult;
import kr.securityadvisor.llm.repository.InquiryMessageRepository;
import kr.securityadvisor.llm.repository.InquiryRepository;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.service.ReviewerAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 담당자 AI 질의(F-04): 배정된 신청의 답변·공개 지식·기관 사실(역할로 걸러진 것)을 컨텍스트로 이어지는 대화.
 * 매 턴 서버가 검색·주입하고 최근 메시지를 함께 보낸다(대화 기억). 답변의 내부 정보는 사실 블록과 대조해 가린다.
 * 업무 상태는 바꾸지 않는다. 감사 이력에는 대화·메시지 ID 와 모델 참조만.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class InquiryService {

    private static final String SYSTEM = """
            너는 공공기관 정보보안·ICT인프라 담당자를 돕는 검토 보조자다. 담당자가 맡은 신청 1건에 대해서만 답한다.
            1. [신청 내용]·[기관 사실]·[공개 지식]에 있는 것만 근거로 답하고, 모르면 모른다고 한다.
            2. 기관 내부 정보(서버명·IP·대역·자산)는 [기관 사실]에 있는 것만 인용하고 그 외는 절대 추측하지 않는다.
            3. 검토 판단은 담당자가 한다. 너는 확인할 점과 근거를 제시한다.
            4. 출력은 JSON 하나만: {"answer":"...","evidence":["S12","P7"]} — evidence 에는 실제 인용한 태그만 넣는다.
            """;
    private static final int HISTORY_WINDOW = 10;

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository messageRepository;
    private final RequestAccessPolicy accessPolicy;
    private final ReviewerAccessPolicy reviewerAccessPolicy;
    private final AnswerService answerService;
    private final KnowledgeSearch knowledgeSearch;
    private final PrivateContextLookup privateContextLookup;
    private final LlmGateway llmGateway;
    private final InternalInfoGuard guard;
    private final AuditRecorder auditRecorder;
    private final JsonCodec json;
    private final ObjectMapper objectMapper;

    /** 대화 생성 — 그 신청의 분야에 배정된 담당자만. */
    public InquiryResponse open(MemberPrincipal principal, Long securityRequestId, InquiryCommand command) {
        ReviewField field = fieldOf(principal);
        accessPolicy.viewableRequest(principal, securityRequestId);
        reviewerAccessPolicy.requireAssignedReviewer(principal, securityRequestId, field);
        Inquiry inquiry = inquiryRepository.save(
                Inquiry.open(securityRequestId, principal.username(), field, command == null ? null : command.title()));
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                null,
                AuditAction.INQUIRY_OPENED,
                null,
                "AI 질의 대화 생성 (대화 " + inquiry.getId() + ")");
        return InquiryResponse.from(inquiry);
    }

    @Transactional(readOnly = true)
    public List<InquiryResponse> listMine(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return inquiryRepository
                .findBySecurityRequestIdAndStaffUsernameOrderByIdDesc(securityRequestId, principal.username())
                .stream()
                .map(InquiryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InquiryMessageResponse> messages(MemberPrincipal principal, Long inquiryId) {
        Inquiry inquiry = requireOwned(principal, inquiryId);
        return messageRepository.findByInquiryIdOrderByIdAsc(inquiry.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 질문 한 턴: 컨텍스트 수집 → 모델 호출 → 검증 → 질문·답변 저장. */
    public InquiryTurnResult ask(MemberPrincipal principal, Long inquiryId, InquiryMessageCommand command) {
        Inquiry inquiry = requireOwned(principal, inquiryId);
        SecurityRequest request = accessPolicy.viewableRequest(principal, inquiry.getSecurityRequestId());
        reviewerAccessPolicy.requireAssignedReviewer(principal, request.getId(), inquiry.getField());

        List<AnswerResponse> answers = answerService.allAnswers(request);
        List<String> answerLines = answers.stream()
                .filter(a -> a.value() != null && !a.value().isBlank())
                .map(a -> "- " + a.fieldKey() + " = " + a.value() + " (출처 " + a.valueSource()
                        + (a.confirmed() ? ", 확인됨" : ", 미확정") + ")")
                .toList();
        List<String> texts = new ArrayList<>(answers.stream()
                .map(AnswerResponse::value)
                .filter(v -> v != null)
                .toList());
        texts.add(command.text());
        List<FactRecord> facts = privateContextLookup.matchIn(principal, texts);
        List<SnippetHit> hits = knowledgeSearch.search(command.text(), 3);
        List<InquiryMessage> recent = new ArrayList<>(messageRepository.findTop10ByInquiryIdOrderByIdDesc(inquiryId));
        java.util.Collections.reverse(recent);

        LlmPrompt prompt = new LlmPrompt(
                SYSTEM + "\n[신청 내용] 서류 " + request.getDocType().code() + ", " + request.getStage() + "단계 "
                        + request.getStatus().label() + "\n"
                        + (answerLines.isEmpty() ? "(답변 없음)" : String.join("\n", answerLines)),
                hits.stream()
                        .map(h -> "[" + h.tag() + "] " + h.citation() + " — " + h.body())
                        .toList(),
                facts.stream().map(f -> "[" + f.tag() + "] " + f.line()).toList(),
                recent.stream()
                        .map(m -> new LlmPrompt.LlmMessage(m.getRole().name(), m.getText()))
                        .toList(),
                command.text());
        LlmResult result = llmGateway.complete(
                LlmPurpose.INQUIRY,
                prompt,
                LlmCallContext.of(principal, AuditTargetType.SECURITY_REQUEST, request.getId()));

        Parsed parsed = parse(result.text());
        Map<String, String> labels = new LinkedHashMap<>();
        hits.forEach(h -> labels.put(h.tag(), h.citation()));
        facts.forEach(f -> labels.put(f.tag(), f.name()));
        List<String> refs = parsed.evidence().stream()
                .filter(labels::containsKey)
                .distinct()
                .toList();
        List<String> unbacked = guard.findUnbacked(parsed.answer(), privateContextLookup.knownValues(principal));
        String answerText = parsed.answer();
        for (String token : unbacked) {
            answerText = answerText.replace(token, "[근거 없는 내부 정보 — 담당자 확인 필요]");
        }

        InquiryMessage question =
                messageRepository.save(InquiryMessage.user(inquiryId, command.text(), json.writeStrings(List.of())));
        InquiryMessage answer = messageRepository.save(InquiryMessage.assistant(
                inquiryId, answerText, json.writeStrings(refs), result.modelReference(), !unbacked.isEmpty()));
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(request.getId()),
                null,
                AuditAction.INQUIRY_ASKED,
                null,
                "AI 질의 (대화 " + inquiryId + ", 메시지 " + question.getId() + "→" + answer.getId() + ", "
                        + result.modelReference() + ", 인용 " + refs
                        + (unbacked.isEmpty() ? "" : ", 내부 정보 가림 " + unbacked.size() + "건") + ")");
        return new InquiryTurnResult(
                InquiryMessageResponse.from(question, List.of()),
                InquiryMessageResponse.from(
                        answer,
                        refs.stream()
                                .map(tag -> new InquiryMessageResponse.Evidence(tag, labels.get(tag)))
                                .toList()));
    }

    private record Parsed(String answer, List<String> evidence) {}

    private Parsed parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode root = objectMapper.readTree(text.substring(start, end + 1));
                if (root.has("answer")) {
                    List<String> evidence = new ArrayList<>();
                    root.path("evidence").forEach(e -> evidence.add(e.asString("")));
                    return new Parsed(root.path("answer").asString(""), evidence);
                }
            } catch (RuntimeException ignored) {
                // 형식을 어긴 응답은 원문 그대로 답변으로 둔다(근거 없음)
            }
        }
        return new Parsed(text, List.of());
    }

    private InquiryMessageResponse toResponse(InquiryMessage m) {
        // 저장된 태그는 표기를 다시 찾지 않고 태그만 보여준다(화면은 태그로 조각·사실을 조회할 수 있다)
        return InquiryMessageResponse.from(
                m,
                json.readStrings(m.getEvidenceRefs()).stream()
                        .map(tag -> new InquiryMessageResponse.Evidence(tag, null))
                        .toList());
    }

    private Inquiry requireOwned(MemberPrincipal principal, Long inquiryId) {
        Inquiry inquiry = inquiryRepository
                .findById(inquiryId)
                .orElseThrow(() -> new NotFoundException("질의 대화를 찾을 수 없습니다: " + inquiryId));
        if (!inquiry.isOwnedBy(principal.username())) {
            throw new NotFoundException("질의 대화를 찾을 수 없습니다: " + inquiryId);
        }
        return inquiry;
    }

    private static ReviewField fieldOf(MemberPrincipal principal) {
        if (principal.hasRole(MemberRole.INFRA)) {
            return ReviewField.INFRA;
        }
        if (principal.hasRole(MemberRole.SECURITY)) {
            return ReviewField.SECURITY;
        }
        throw new ForbiddenException("AI 질의는 INFRA·SECURITY 담당자만 할 수 있습니다");
    }
}
