package kr.securityadvisor.request.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.request.dto.ConsultationOverview;
import kr.securityadvisor.request.dto.ConsultationOverview.*;
import kr.securityadvisor.request.repository.ConsultationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
@RequiredArgsConstructor
public class ConsultationComparisonService {
    private final RequestAccessPolicy access;
    private final AnswerService answers;
    private final InfrastructurePlanService plans;
    private final PublicationService publications;
    private final ConsultationRecordRepository repository;
    private final ConsultationRecorder recorder;
    private final ObjectMapper json;
    private final jakarta.persistence.EntityManager entityManager;

    public void baseline(MemberPrincipal actor, Long id) {
        var request = access.viewableRequest(actor, id);
        entityManager.lock(request, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.lock(
                access.viewableCase(actor, request.getBusinessCaseId()),
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        recorder.baseline(actor, id, current(actor, id));
    }

    private Map<String, Field> current(MemberPrincipal actor, Long id) {
        var request = access.viewableRequest(actor, id);
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        var schema = publications.getFieldSchema(business.getPublicationId());
        Map<String, Field> values = new TreeMap<>();
        values.put("business:title", new Field("사업명", "내 사업", business.getTitle(), true, ""));
        values.put("business:department", new Field("담당부서", "내 사업", business.getDepartment(), true, ""));
        for (var a : answers.allAnswers(request))
            values.put(
                    "answer:" + a.fieldKey(),
                    ConsultationFields.answer(
                            schema.find(a.fieldKey()).map(f -> f.label()).orElse(a.fieldKey()),
                            a.scope().name().equals("CASE") ? "사업 정보" : "신청 정보",
                            a));
        values.putAll(ConsultationFields.plan(plans.snapshot(id)));
        return values;
    }

    @Transactional(readOnly = true)
    public ConsultationOverview get(MemberPrincipal actor, Long id) {
        var request = access.viewableRequest(actor, id);
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        var records = repository.findByRequestIdOrderByIdAsc(id);
        var baseline = records.stream()
                .filter(r -> r.getKind().equals("BASELINE"))
                .reduce((a, b) -> b)
                .orElse(null);
        var current = current(actor, id);
        var changes = records.stream()
                .filter(r -> !Set.of("BASELINE", "SUMMARY").contains(r.getKind())
                        && (baseline == null || r.getId() > baseline.getId()))
                .map(r -> {
                    var p = recorder.read(r);
                    return new Change(
                            r.getId(),
                            r.getKind(),
                            r.getActorName(),
                            r.getActorRole(),
                            r.getCreatedAt(),
                            p.before(),
                            p.after());
                })
                .toList();
        String fingerprint = hash(json.writeValueAsString(
                List.of(request.getRevision(), current, baseline == null ? 0 : baseline.getId(), changes)));
        var summary = records.stream()
                .filter(r -> r.getKind().equals("SUMMARY"))
                .reduce((a, b) -> b)
                .map(r -> json.readValue(r.getPayload(), Summary.class))
                .orElse(null);
        return new ConsultationOverview(
                fingerprint,
                baseline != null,
                baseline == null ? null : baseline.getCreatedAt(),
                business.getTitle(),
                business.getDepartment(),
                baseline == null ? Map.of() : recorder.read(baseline).after(),
                current,
                changes,
                summary);
    }

    public ConsultationOverview prepare(MemberPrincipal actor, Long id) {
        var request = access.ownedRequest(actor, id);
        if (request.getStage() != 3 || request.getStatus() != RequestStatus.FINAL_REVIEW)
            throw new DomainRuleViolationException("사용자 최종 검토 단계에서만 협의 결과 요약을 생성할 수 있습니다.");
        return get(actor, id);
    }

    public Summary saveSummary(MemberPrincipal actor, Long id, Summary summary) {
        var request = access.ownedRequest(actor, id);
        entityManager.refresh(request, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.lock(
                access.viewableCase(actor, request.getBusinessCaseId()),
                jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var current = prepare(actor, id);
        if (!current.fingerprint().equals(summary.fingerprint()))
            throw new ConflictException("요약 중 원본이 변경되었습니다. 최신 내용을 불러온 뒤 다시 요약해 주세요.");
        recorder.append(actor, id, "SUMMARY", summary);
        return summary;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
