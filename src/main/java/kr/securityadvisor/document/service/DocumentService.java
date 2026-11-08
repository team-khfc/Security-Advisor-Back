package kr.securityadvisor.document.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.document.domain.DocumentKind;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.dto.GenerateDocumentCommand;
import kr.securityadvisor.document.dto.GeneratedDocumentResponse;
import kr.securityadvisor.document.repository.GeneratedDocumentRepository;
import kr.securityadvisor.installation.InstallationProperties;
import kr.securityadvisor.knowledge.service.PrivateContextLookup;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.service.InternalInfoGuard;
import kr.securityadvisor.llm.service.LlmCallContext;
import kr.securityadvisor.llm.service.LlmGateway;
import kr.securityadvisor.llm.service.LlmPrompt;
import kr.securityadvisor.llm.service.LlmResult;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 서류 생성·조회·다운로드(F-06, F-07 문서함 다운로드). 초안은 현재 답변으로, 제출본/확정본은 제출본 스냅샷으로 만든다.
 * AI 보완 서술은 선택이며 값에 반영되지 않는 미확정 참고 문장이다(원칙 1). 1차 범위 형식은 Markdown.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    private static final String SYSTEM = """
            너는 공공기관 보안 서류 작성 보조자다. 주어진 항목 값만 바탕으로, 서류 담당자가 읽을 "사업 및 요청 개요" 서술을 한국어 3~6문장으로 쓴다.
            값에 없는 내용을 덧붙이지 말고, 서버명·IP·대역 등 내부 정보는 값에 있는 그대로만 쓴다. 미확정(확인 전) 값은 "확인 예정"이라고 표시한다.
            출력은 서술 문단만.
            """;
    private static final TypeReference<List<DocumentSection>> SECTIONS = new TypeReference<>() {};

    private final GeneratedDocumentRepository repository;
    private final RequestAccessPolicy accessPolicy;
    private final PublicationService publicationService;
    private final AnswerService answerService;
    private final SubmissionService submissionService;
    private final DocumentRenderer renderer;
    private final LlmGateway llmGateway;
    private final InternalInfoGuard guard;
    private final PrivateContextLookup privateContextLookup;
    private final InstallationProperties installation;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GeneratedDocumentResponse generate(
            MemberPrincipal principal, Long securityRequestId, GenerateDocumentCommand command) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        String docTypeName = schema.findDocType(request.getDocType())
                .map(d -> d.name())
                .orElse(request.getDocType().code());

        Long submissionId = command == null ? null : command.submissionId();
        DocumentKind kind;
        Map<String, DocumentRenderer.Source> sources;
        if (submissionId == null) {
            kind = DocumentKind.DRAFT;
            sources = fromCurrentAnswers(request);
        } else {
            Submission submission = submissionService.requireSubmission(submissionId);
            if (!submission.getSecurityRequestId().equals(securityRequestId)) {
                throw new DomainRuleViolationException("다른 신청의 제출본입니다: " + submissionId);
            }
            boolean confirmed =
                    request.getStatus() == RequestStatus.CONFIRMED && submissionService.isLatest(submission);
            kind = confirmed ? DocumentKind.CONFIRMED : DocumentKind.SUBMITTED;
            sources = fromSnapshot(submission.getSnapshot());
        }

        List<DocumentSection> sections = renderer.sections(schema, request, sources);
        Instant now = clock.instant();
        String aiSupplement = null;
        String modelReference = null;
        if (command != null && Boolean.TRUE.equals(command.aiSupplement())) {
            LlmResult result = llmGateway.complete(
                    LlmPurpose.DOCUMENT,
                    LlmPrompt.of(SYSTEM, List.of(), List.of(), valuesText(sections)),
                    LlmCallContext.of(principal, AuditTargetType.SECURITY_REQUEST, securityRequestId));
            aiSupplement = result.text();
            for (String token : guard.findUnbacked(aiSupplement, knownValues(principal, sources))) {
                aiSupplement = aiSupplement.replace(token, "[근거 없는 내부 정보 — 담당자 확인 필요]");
            }
            modelReference = result.modelReference();
        }
        String markdown = renderer.markdown(
                installation.displayName(),
                docTypeName,
                businessCase.getTitle(),
                kind,
                request,
                sections,
                aiSupplement,
                modelReference,
                now);
        GeneratedDocument document = repository.save(GeneratedDocument.render(
                securityRequestId,
                submissionId,
                kind,
                repository.countBySecurityRequestId(securityRequestId) + 1,
                docTypeName + " — " + businessCase.getTitle(),
                objectMapper.writeValueAsString(sections),
                markdown,
                aiSupplement,
                modelReference,
                renderer.unconfirmedCount(sections),
                principal.username(),
                now));
        auditRecorder.record(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(securityRequestId),
                request.getRevision(),
                AuditAction.DOCUMENT_GENERATED,
                null,
                kind.label() + " 생성 (서류 " + document.getId() + ", 버전 " + document.getVersion() + ", 미확정 "
                        + document.getUnconfirmedCount() + "건"
                        + (modelReference == null ? "" : ", AI 보완 " + modelReference) + ")");
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> listForRequest(MemberPrincipal principal, Long securityRequestId) {
        accessPolicy.viewableRequest(principal, securityRequestId);
        return repository.findBySecurityRequestIdOrderByVersionDesc(securityRequestId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentResponse get(MemberPrincipal principal, Long documentId) {
        return toResponse(requireViewable(principal, documentId));
    }

    /** 다운로드용 본문·파일명. 1차 범위는 Markdown(UTF-8). */
    @Transactional(readOnly = true)
    public Download download(MemberPrincipal principal, Long documentId) {
        GeneratedDocument d = requireViewable(principal, documentId);
        String fileName = d.getKind().label() + "_" + d.getSecurityRequestId() + "_v" + d.getVersion() + ".md";
        auditRecorder.recordAccess(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(d.getSecurityRequestId()),
                AuditAction.DOCUMENT_DOWNLOADED,
                d.getKind().label() + " v" + d.getVersion() + " 다운로드 (서류 " + d.getId() + ", " + fileName + ")");
        return new Download(fileName, d.getRenderedMarkdown().getBytes(StandardCharsets.UTF_8));
    }

    public record Download(String fileName, byte[] bytes) {}

    private GeneratedDocument requireViewable(MemberPrincipal principal, Long documentId) {
        GeneratedDocument d = repository
                .findById(documentId)
                .orElseThrow(() -> new NotFoundException("서류를 찾을 수 없습니다: " + documentId));
        accessPolicy.viewableRequest(principal, d.getSecurityRequestId());
        return d;
    }

    private Map<String, DocumentRenderer.Source> fromCurrentAnswers(SecurityRequest request) {
        Map<String, DocumentRenderer.Source> sources = new LinkedHashMap<>();
        answerService
                .allAnswers(request)
                .forEach(a -> sources.put(
                        a.fieldKey(),
                        new DocumentRenderer.Source(a.value(), a.valueSource(), a.confirmed(), a.needsReview())));
        return sources;
    }

    private Map<String, DocumentRenderer.Source> fromSnapshot(String snapshotJson) {
        Map<String, DocumentRenderer.Source> sources = new LinkedHashMap<>();
        JsonNode root = objectMapper.readTree(snapshotJson);
        for (JsonNode a : root.path("answers")) {
            String vs = a.path("valueSource").isNull()
                    ? null
                    : a.path("valueSource").asString(null);
            ValueSource source = vs == null || vs.isBlank() ? null : ValueSource.valueOf(vs);
            boolean confirmed =
                    source == ValueSource.USER_ANSWER || !a.path("confirmedBy").isNull();
            sources.put(
                    a.path("fieldKey").asString(""),
                    new DocumentRenderer.Source(
                            a.path("value").isNull() ? null : a.path("value").asString(null),
                            source,
                            confirmed,
                            false));
        }
        return sources;
    }

    private static String valuesText(List<DocumentSection> sections) {
        StringBuilder sb = new StringBuilder();
        sections.forEach(s -> s.values()
                .forEach(v -> sb.append("- ")
                        .append(v.label())
                        .append(": ")
                        .append(v.display())
                        .append(" [")
                        .append(v.status())
                        .append("]\n")));
        return sb.toString();
    }

    private List<String> knownValues(MemberPrincipal principal, Map<String, DocumentRenderer.Source> sources) {
        List<String> known = new java.util.ArrayList<>(privateContextLookup.knownValues(principal));
        sources.values().stream()
                .map(DocumentRenderer.Source::value)
                .filter(v -> v != null)
                .forEach(known::add);
        return known;
    }

    private GeneratedDocumentResponse toResponse(GeneratedDocument d) {
        return GeneratedDocumentResponse.from(d, objectMapper.readValue(d.getSections(), SECTIONS));
    }
}
