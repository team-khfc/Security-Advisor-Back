package kr.securityadvisor.document.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.schema.DocTypeDefinition;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.document.domain.DocumentKind;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.dto.DocumentValue;
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
import kr.securityadvisor.request.service.FinalDraftService;
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
    private final FinalDraftService finalDraftService;
    private final DocumentRenderer renderer;
    private final LlmGateway llmGateway;
    private final InternalInfoGuard guard;
    private final PrivateContextLookup privateContextLookup;
    private final InstallationProperties installation;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final DocumentExporters exporters;
    private final Clock clock;
    private final kr.securityadvisor.request.service.InfrastructurePlanService infrastructurePlans;
    private final EntityManager entityManager;

    public GeneratedDocumentResponse generate(
            MemberPrincipal principal, Long securityRequestId, GenerateDocumentCommand command) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        // 두 다운로드가 동시에 문서를 생성해도 같은 버전 번호를 할당하지 않는다.
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
        String docTypeName = schema.findDocType(request.getDocType())
                .map(d -> d.name())
                .orElse(request.getDocType().code());

        Long submissionId = command == null ? null : command.submissionId();
        String caseTitle = businessCase.getTitle();
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
            caseTitle = objectMapper
                    .readTree(submission.getSnapshot())
                    .path("caseTitle")
                    .asString(caseTitle);
        }

        List<DocumentSection> sections =
                withFinalDraft(renderer.sections(schema, request, sources), principal, request, submissionId);
        sections = new ArrayList<>(sections);
        var plan = submissionId == null
                ? infrastructurePlans.snapshot(request.getId())
                : objectMapper
                                .readTree(submissionService
                                        .requireSubmission(submissionId)
                                        .getSnapshot())
                                .hasNonNull("infrastructurePlan")
                        ? objectMapper.treeToValue(
                                objectMapper
                                        .readTree(submissionService
                                                .requireSubmission(submissionId)
                                                .getSnapshot())
                                        .path("infrastructurePlan"),
                                kr.securityadvisor.request.dto.InfrastructurePlanResponse.class)
                        : null;
        if (plan != null && !plan.connections().isEmpty()) {
            int index = 0;
            for (var row : plan.connections()) {
                String key = "infrastructure.connection." + (++index);
                String value = row.sourceSystem() + " (" + row.sourceZone() + ", " + row.sourceAddress() + ") → "
                        + row.destinationSystem() + " (" + row.destinationZone() + ", " + row.destinationAddress()
                        + ") · " + row.direction() + " · " + row.protocol() + " / " + row.port() + " · "
                        + row.startDate() + " ~ " + row.endDate() + " · 목적: " + row.purpose();
                sections.add(new DocumentSection(
                        "통신 구간 " + index,
                        List.of(new DocumentValue(
                                key, "서버·네트워크 구성", "REQUEST", value, "STAFF_INPUT", true, false, value, "담당자 입력"))));
            }
            if (!plan.notes().isBlank())
                sections.add(new DocumentSection(
                        "ICT인프라 검토 사항",
                        List.of(new DocumentValue(
                                "infrastructure.notes",
                                "검토 사항",
                                "REQUEST",
                                plan.notes(),
                                "STAFF_INPUT",
                                true,
                                false,
                                plan.notes(),
                                "담당자 입력"))));
        }
        Instant now = clock.instant();
        String aiSupplement = null;
        String modelReference = null;
        if (command != null && Boolean.TRUE.equals(command.aiSupplement())) {
            LlmResult result = llmGateway.complete(
                    LlmPurpose.DOCUMENT,
                    LlmPrompt.of(SYSTEM, List.of(), List.of(), valuesText(sections)),
                    LlmCallContext.of(principal, AuditTargetType.SECURITY_REQUEST, securityRequestId));
            aiSupplement = result.text();
            for (String token : guard.findUnbacked(
                    aiSupplement, knownValues(principal, sources, businessCase.getKnowledgePublicationId()))) {
                aiSupplement = aiSupplement.replace(token, "[근거 없는 내부 정보 — 담당자 확인 필요]");
            }
            modelReference = result.modelReference();
        }
        String markdown = renderer.markdown(
                installation.displayName(),
                docTypeName,
                caseTitle,
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
                docTypeName + " — " + caseTitle,
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

    /**
     * 다운로드용 바이트·파일명. 형식은 게시본의 서류 유형 정의(exportFormats)가 허용하고 변환기 구현이 있는 것만.
     *
     * @throws DomainRuleViolationException 이 서류 유형에 허용되지 않은 형식(422)
     * @throws ServiceUnavailableException 형식은 허용됐지만 변환기 구현이 없을 때(503, 예: HWPX)
     */
    @Transactional(readOnly = true)
    public Download download(MemberPrincipal principal, Long documentId, ExportFormat format) {
        GeneratedDocument d = requireViewable(principal, documentId);
        SecurityRequest request = accessPolicy.viewableRequest(principal, d.getSecurityRequestId());
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        DocTypeDefinition docType = publicationService
                .getFieldSchema(businessCase.getPublicationId())
                .findDocType(request.getDocType())
                .orElseThrow(() -> new NotFoundException("게시본에 없는 서류 유형입니다: " + request.getDocType()));
        if (!docType.allowsExport(format)) {
            throw new DomainRuleViolationException(
                    docType.name() + " 은(는) " + format + " 형식을 허용하지 않습니다 (허용: " + docType.exportFormats() + ")");
        }
        DocumentExporter exporter = exporters
                .find(format)
                .orElseThrow(() -> new ServiceUnavailableException(format + " 출력은 아직 구현되지 않았습니다"));
        String fileName =
                d.getKind().label() + "_" + d.getSecurityRequestId() + "_v" + d.getVersion() + "." + format.extension();
        byte[] bytes = exporter.export(d, objectMapper.readValue(d.getSections(), SECTIONS));
        auditRecorder.recordAccess(
                principal,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(d.getSecurityRequestId()),
                AuditAction.DOCUMENT_DOWNLOADED,
                d.getKind().label() + " v" + d.getVersion() + " " + format + " 다운로드 (서류 " + d.getId() + ", " + fileName
                        + ", " + bytes.length + " bytes)");
        return new Download(fileName, format, bytes);
    }

    /**
     * 서류 미리보기 데이터(JSON): 저장하지 않고 현재 답변(또는 제출본 스냅샷)으로 섹션을 렌더링해 돌려준다.
     * 화면이 "문서 데이터 내려받기"로 쓰는 경량 경로 — 정식 출력은 generate → download 다. 조회 감사는 남기지 않는다(저장물이 아님).
     */
    @Transactional(readOnly = true)
    public DocumentData previewData(MemberPrincipal principal, Long securityRequestId, Long submissionId) {
        SecurityRequest request = accessPolicy.viewableRequest(principal, securityRequestId);
        BusinessCase businessCase = accessPolicy.viewableCase(principal, request.getBusinessCaseId());
        String caseTitle = businessCase.getTitle();
        FieldSchema schema = publicationService.getFieldSchema(businessCase.getPublicationId());
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
            kind = request.getStatus() == RequestStatus.CONFIRMED && submissionService.isLatest(submission)
                    ? DocumentKind.CONFIRMED
                    : DocumentKind.SUBMITTED;
            sources = fromSnapshot(submission.getSnapshot());
            caseTitle = objectMapper
                    .readTree(submission.getSnapshot())
                    .path("caseTitle")
                    .asString(caseTitle);
        }
        List<DocumentSection> sections =
                withFinalDraft(renderer.sections(schema, request, sources), principal, request, submissionId);
        return new DocumentData(
                securityRequestId,
                submissionId,
                kind,
                kind.label(),
                schema.findDocType(request.getDocType())
                        .map(d -> d.name())
                        .orElse(request.getDocType().code()),
                caseTitle,
                sections,
                renderer.unconfirmedCount(sections),
                clock.instant());
    }

    /** 미리보기 데이터(저장 안 함). */
    public record DocumentData(
            Long securityRequestId,
            Long submissionId,
            DocumentKind kind,
            String kindLabel,
            String docTypeName,
            String caseTitle,
            List<DocumentSection> sections,
            int unconfirmedCount,
            Instant generatedAt) {}

    /** 출력 형식 목록 — 구현 여부를 함께 준다(화면은 미구현 형식을 "준비 중"으로 표시). */
    public List<ExportFormatInfo> exportFormats() {
        return java.util.Arrays.stream(ExportFormat.values())
                .map(f -> new ExportFormatInfo(f, f.extension(), f.mediaType(), exporters.supports(f)))
                .toList();
    }

    public record Download(String fileName, ExportFormat format, byte[] bytes) {}

    /** 출력 형식 안내. */
    public record ExportFormatInfo(ExportFormat format, String extension, String mediaType, boolean implemented) {}

    private GeneratedDocument requireViewable(MemberPrincipal principal, Long documentId) {
        GeneratedDocument d = repository
                .findById(documentId)
                .orElseThrow(() -> new NotFoundException("서류를 찾을 수 없습니다: " + documentId));
        accessPolicy.viewableRequest(principal, d.getSecurityRequestId());
        return d;
    }

    /** 제출 이후에는 현재 초안을 읽지 않고 제출 시 보존한 서술만 출력한다. */
    private List<DocumentSection> withFinalDraft(
            List<DocumentSection> sections, MemberPrincipal principal, SecurityRequest request, Long submissionId) {
        String text;
        String source;
        String confirmation;
        boolean confirmed;
        boolean stale = false;
        if (submissionId != null) {
            JsonNode draft = objectMapper
                    .readTree(submissionService.requireSubmission(submissionId).getSnapshot())
                    .path("finalDraft");
            if (draft.isMissingNode() || draft.isNull()) return sections;
            text = draft.path("text").asString();
            source = draft.path("valueSource").asString();
            confirmed = !draft.path("confirmedAt").isNull()
                    && !draft.path("confirmedAt").isMissingNode();
            confirmation = "사용자 확인 · " + draft.path("confirmedBy").asString() + " · "
                    + draft.path("confirmedAt").asString();
        } else {
            var draft = finalDraftService.current(principal, request.getId());
            if (draft == null) return sections;
            text = draft.text();
            source = draft.valueSource();
            stale = draft.stale();
            confirmed = draft.confirmed() && !stale;
            confirmation = confirmed
                    ? "사용자 확인 · " + draft.confirmedBy() + " · " + draft.confirmedAt()
                    : stale ? "원본 변경 · 재확인 필요" : "미확정 · 사용자 확인 필요";
        }
        List<DocumentSection> result = new ArrayList<>(sections);
        result.add(new DocumentSection(
                "최종 보고서",
                List.of(new DocumentValue(
                        "report.finalSummary",
                        "최종 서술",
                        "REQUEST",
                        text,
                        source,
                        confirmed,
                        stale,
                        text,
                        confirmation))));
        return result;
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

    private List<String> knownValues(
            MemberPrincipal principal, Map<String, DocumentRenderer.Source> sources, Long knowledgePublicationId) {
        List<String> known =
                new java.util.ArrayList<>(privateContextLookup.knownValues(principal, knowledgePublicationId));
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
