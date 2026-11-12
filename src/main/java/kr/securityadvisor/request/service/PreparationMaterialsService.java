package kr.securityadvisor.request.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import kr.securityadvisor.audit.domain.*;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.dto.PreparationMaterialsResponse.PreparationItem;
import kr.securityadvisor.request.repository.PreparationMaterialsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 사업 조건은 기존 답변에서 읽고 파일 연결·검토 행위만 별도 저장한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreparationMaterialsService {
    private final PreparationMaterialsRepository repository;
    private final RequestAccessPolicy access;
    private final AnswerService answers;
    private final UploadedFileService files;
    private final AuditRecorder audit;
    private final EntityManager entities;
    private final ObjectMapper json;
    private final Clock clock;
    private final kr.securityadvisor.assignment.service.AssignmentService assignments;
    private final kr.securityadvisor.notification.service.Notifier notifier;

    /** 현재 권한을 확인한 뒤 제출·최종 검토에서 공유하는 자료 묶음을 만든다. 미첨부는 제출 차단 사유가 아니다. */
    public PreparationReviewResponse finalReview(MemberPrincipal actor, Long id) {
        var request = access.viewableRequest(actor, id);
        var current = get(actor, id);
        var linked = files.list(actor, request.getBusinessCaseId()).stream()
                .collect(java.util.stream.Collectors.toMap(UploadedFileResponse::uploadedFileId, f -> f));
        return new PreparationReviewResponse(
                1,
                current.revision(),
                current.fingerprint(),
                clock.instant(),
                current.extraConditions(),
                current.items().stream()
                        .map(i -> new PreparationReviewResponse.Item(
                                i.key(),
                                i.label(),
                                i.reviewer(),
                                i.applicability(),
                                i.status(),
                                i.stale(),
                                i.note(),
                                i.actor(),
                                i.at(),
                                i.fileIds().stream()
                                        .map(fileId -> {
                                            var f = linked.get(fileId);
                                            if (f == null)
                                                throw new ConflictException("연결된 자료를 찾을 수 없습니다. 최신 목록을 확인해 주세요.");
                                            return new PreparationReviewResponse.File(
                                                    fileId, f.originalName(), f.uploadedAt());
                                        })
                                        .toList()))
                        .toList());
    }

    public record Entry(List<Long> files, String status, String note, String actor, Instant at, String fingerprint) {}

    public record Content(Map<String, Boolean> conditions, Map<String, Entry> entries, long conditionsRevision) {}

    private Content content(PreparationMaterials value) {
        return value.getContent() == null
                ? new Content(new TreeMap<>(), new TreeMap<>(), 0)
                : json.readValue(value.getContent(), Content.class);
    }
    /** 현재 조건을 계산하는 조회는 DB에 새 행을 만들지 않는다. */
    public PreparationMaterialsResponse get(MemberPrincipal actor, Long id) {
        var request = access.viewableRequest(actor, id);
        return response(actor, request, repository.findById(id).orElseGet(() -> PreparationMaterials.create(id)));
    }

    private PreparationMaterialsResponse response(
            MemberPrincipal actor, SecurityRequest request, PreparationMaterials value) {
        var data = content(value);
        var all = answers.allAnswers(request);
        Map<String, Boolean> conditions = new HashMap<>(data.conditions());
        for (var requirement : PreparationPolicy.REQUIREMENTS) {
            if (requirement.condition().isBlank() || Set.of("cloud", "ai").contains(requirement.condition())) continue;
            var answer = all.stream()
                    .filter(a -> a.fieldKey().equals(requirement.condition()))
                    .findFirst()
                    .orElse(null);
            conditions.put(
                    requirement.condition(),
                    answer == null
                            ? null
                            : PreparationPolicy.condition(answer.value(), answer.confirmed(), answer.needsReview()));
        }
        String fingerprint;
        try {
            var relevant = all.stream()
                    .filter(a -> PreparationPolicy.REQUIREMENTS.stream()
                            .anyMatch(r -> r.condition().equals(a.fieldKey())))
                    .toList();
            fingerprint = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(json.writeValueAsString(List.of(
                                            relevant, new TreeMap<>(data.conditions()), data.conditionsRevision()))
                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        boolean editable = request.isUnsubmitted() && !request.isTerminal();
        List<PreparationItem> items = new ArrayList<>();
        for (var requirement : PreparationPolicy.REQUIREMENTS) {
            Boolean enabled =
                    requirement.condition().isBlank() ? Boolean.TRUE : conditions.get(requirement.condition());
            String applies = enabled == null ? "UNKNOWN" : enabled ? "APPLICABLE" : "NOT_APPLICABLE";
            Entry entry = data.entries().get(requirement.key());
            boolean stale = entry != null && !fingerprint.equals(entry.fingerprint());
            String status = entry == null || entry.files().isEmpty() ? "MISSING" : stale ? "IN_REVIEW" : entry.status();
            boolean review = editable
                    && request.getStage() >= 2
                    && actor.role().name().equals(requirement.reviewer())
                    && Boolean.TRUE.equals(enabled);
            boolean attach = editable
                    && actor.role() != MemberRole.ADMIN
                    && (actor.role() == MemberRole.APPLICANT
                            || actor.role().name().equals(requirement.reviewer()))
                    && !Boolean.FALSE.equals(enabled);
            items.add(new PreparationItem(
                    requirement.key(),
                    requirement.label(),
                    requirement.reviewer(),
                    requirement.condition(),
                    requirement.description(),
                    applies,
                    status,
                    entry == null ? List.of() : entry.files(),
                    entry == null ? "" : entry.note(),
                    entry == null ? null : entry.actor(),
                    entry == null ? null : entry.at(),
                    stale,
                    attach,
                    review));
        }
        return new PreparationMaterialsResponse(
                value.getRevision(),
                fingerprint,
                data.conditions(),
                editable,
                editable && actor.role() == MemberRole.APPLICANT,
                items);
    }
    /** 조건 변경·자료 교체·검토 결과를 감사 이력과 함께 저장한다. */
    @Transactional
    public PreparationMaterialsResponse update(MemberPrincipal actor, Long id, PreparationMaterialsCommand command) {
        var request = access.viewableRequest(actor, id);
        entities.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        entities.refresh(business, LockModeType.PESSIMISTIC_WRITE);
        var value = repository.findById(id).orElseGet(() -> PreparationMaterials.create(id));
        var current = response(actor, request, value);
        if (value.getRevision() != command.expectedRevision()
                || !current.fingerprint().equals(command.expectedFingerprint()))
            throw new ConflictException("사업 조건 또는 준비자료가 변경됐습니다. 최신 목록을 확인한 뒤 다시 저장해 주세요.");
        if (!current.editable()) throw new DomainRuleViolationException("제출·종료된 신청의 준비자료는 변경할 수 없습니다.");
        var data = content(value);
        Map<String, Boolean> conditions = new TreeMap<>(data.conditions());
        Map<String, Entry> entries = new TreeMap<>(data.entries());
        String summary;
        if ("CONDITIONS".equals(command.action())) {
            actor.requireAnyRole(MemberRole.APPLICANT);
            if (command.conditions() == null
                    || !Set.of("cloud", "ai").containsAll(command.conditions().keySet()))
                throw new DomainRuleViolationException("클라우드·AI 사용 조건만 지정할 수 있습니다.");
            conditions.clear();
            command.conditions().forEach((key, flag) -> {
                if (flag != null) conditions.put(key, flag);
            });
            summary = "준비자료 · 클라우드/AI 사업 조건 변경";
        } else {
            var item = current.items().stream()
                    .filter(i -> i.key().equals(command.key()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("준비자료 항목이 없습니다."));
            String note = command.note() == null ? "" : command.note().trim();
            List<Long> linked;
            String status;
            if ("ATTACH".equals(command.action())) {
                if (!item.canAttach()) throw new ForbiddenException("이 자료를 연결할 권한이 없습니다.");
                linked = command.fileIds() == null
                        ? List.of()
                        : command.fileIds().stream().distinct().toList();
                if (linked.isEmpty()) throw new DomainRuleViolationException("연결할 파일을 선택해 주세요.");
                linked.forEach(file -> files.requireFile(business.getId(), file));
                status = "IN_REVIEW";
            } else {
                if (!item.canReview()) throw new ForbiddenException("조건이 확인된 자료를 배정된 해당 분야 담당자만 검토할 수 있습니다.");
                linked = item.fileIds();
                status = command.status();
                PreparationPolicy.validate(status == null ? "" : status, linked, note);
            }
            entries.put(
                    item.key(),
                    new Entry(linked, status, note, actor.displayName(), clock.instant(), current.fingerprint()));
            summary = "준비자료 · " + item.label() + " · "
                    + Map.of("IN_REVIEW", "검토 중", "NEEDS_SUPPLEMENT", "보완 필요", "CONFIRMED", "확인 완료")
                            .get(status) + " · 파일 " + linked;
        }
        value.replace(
                command.expectedRevision(),
                json.writeValueAsString(new Content(
                        conditions,
                        entries,
                        data.conditionsRevision() + ("CONDITIONS".equals(command.action()) ? 1 : 0))));
        repository.saveAndFlush(value);
        audit.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                id.toString(),
                value.getRevision(),
                AuditAction.PREPARATION_MATERIAL_UPDATED,
                command.note(),
                summary);
        var updated = response(actor, request, value);
        String eventKey = "preparation:" + id + ":" + value.getRevision();
        String link = "/requests/" + id;
        if ("REVIEW".equals(command.action()) && "NEEDS_SUPPLEMENT".equals(command.status())) {
            notifier.notify(
                    business.getApplicantUsername(),
                    eventKey,
                    kr.securityadvisor.notification.domain.NotificationKind.PREPARATION_SUPPLEMENT,
                    "신청 #" + id + " 준비자료 보완이 필요합니다",
                    link);
        } else if ("ATTACH".equals(command.action()) || "CONDITIONS".equals(command.action())) {
            var fields = updated.items().stream()
                    .filter(i -> !"NOT_APPLICABLE".equals(i.applicability())
                            && !i.fileIds().isEmpty())
                    .filter(i ->
                            "CONDITIONS".equals(command.action()) || i.key().equals(command.key()))
                    .map(PreparationItem::reviewer)
                    .collect(java.util.stream.Collectors.toSet());
            notifier.notify(
                    assignments.listForRequest(id).stream()
                            .filter(a -> a.active() && fields.contains(a.field().name()))
                            .map(a -> a.assigneeUsername())
                            .toList(),
                    eventKey,
                    kr.securityadvisor.notification.domain.NotificationKind.PREPARATION_REVIEW,
                    "신청 #" + id + " 준비자료 첨부·조건 변경 검토가 필요합니다",
                    link);
        }
        return updated;
    }
}
