package kr.securityadvisor.request.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.*;
import kr.securityadvisor.assignment.service.AssignmentService;
import kr.securityadvisor.audit.domain.*;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.*;
import kr.securityadvisor.common.schema.*;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.request.domain.*;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.repository.AnswerRepository;
import kr.securityadvisor.request.repository.InfrastructurePlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
@RequiredArgsConstructor
public class InfrastructurePlanService {
    private final InfrastructurePlanRepository repository;
    private final RequestAccessPolicy access;
    private final AssignmentService assignments;
    private final AnswerService answers;
    private final AnswerRepository answerRepository;
    private final PublicationService publications;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final EntityManager entityManager;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InfrastructurePlanResponse get(MemberPrincipal actor, Long id) {
        access.viewableRequest(actor, id);
        return snapshot(id);
    }

    @Transactional(readOnly = true)
    public InfrastructurePlanResponse snapshot(Long id) {
        return repository
                .findById(id)
                .map(p -> {
                    var c = json.readValue(p.getContent(), InfrastructurePlanCommand.class);
                    return new InfrastructurePlanResponse(
                            p.getRevision(), c.connections(), c.notes(), p.getUpdatedBy(), p.getUpdatedAt());
                })
                .orElse(new InfrastructurePlanResponse(0, List.of(), "", null, null));
    }

    public InfrastructurePlanResponse save(MemberPrincipal actor, Long id, InfrastructurePlanCommand command) {
        var request = access.viewableRequest(actor, id);
        actor.requireAnyRole(MemberRole.INFRA);
        if (!assignments.isActiveAssignee(id, ReviewField.INFRA, actor.username()))
            throw new ForbiddenException("배정된 ICT인프라 담당자만 수정할 수 있습니다.");
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        if (request.getStage() != 2 || request.isTerminal())
            throw new DomainRuleViolationException("ICT인프라 검토 정보는 담당자 협의 단계에서만 수정할 수 있습니다.");
        var plan = repository.findById(id).orElseGet(() -> InfrastructurePlan.create(id));
        if (plan.getRevision() != command.expectedRevision())
            throw new ConflictException("다른 담당자가 수정했습니다. 최신 검토 정보를 다시 불러온 후 저장해 주세요.");
        Set<String> ids = new HashSet<>();
        for (var c : command.connections()) {
            if (!ids.add(c.id())) throw new DomainRuleViolationException("통신 구간 식별자가 중복되었습니다.");
            validate("출발지 주소", "IP_ADDRESS", c.sourceAddress());
            validate("목적지 주소", "IP_ADDRESS", c.destinationAddress());
            validate("프로토콜", "PROTOCOL", c.protocol());
            validate("포트", "PORT", c.port());
            validate("허용 기간", "DATE_RANGE", c.startDate() + " ~ " + c.endDate());
            if (!Set.of("외부", "DMZ", "내부망", "클라우드", "기타").contains(c.sourceZone())
                    || !Set.of("외부", "DMZ", "내부망", "클라우드", "기타").contains(c.destinationZone()))
                throw new DomainRuleViolationException("망 구간을 선택해 주세요.");
            if (!Set.of("인바운드", "아웃바운드", "양방향").contains(c.direction()))
                throw new DomainRuleViolationException("통신 방향을 선택해 주세요.");
        }
        var business = access.viewableCase(actor, request.getBusinessCaseId());
        entityManager.refresh(business, LockModeType.PESSIMISTIC_WRITE);
        var schema = publications.getFieldSchema(business.getPublicationId());
        var first = command.connections().getFirst();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("system.target_server", first.destinationSystem());
        values.put("network.source", first.sourceAddress());
        values.put("network.destination", first.destinationAddress());
        values.put("network.source_system", first.sourceSystem());
        values.put("network.destination_system", first.destinationSystem());
        values.put("firewall.protocol", first.protocol());
        values.put("firewall.port", first.port());
        values.put("firewall.period", first.startDate() + " ~ " + first.endDate());
        values.put("firewall.purpose", first.purpose());
        values.put("firewall.direction", first.direction());
        for (var entry : values.entrySet()) {
            var field = schema.find(entry.getKey()).orElse(null);
            if (field == null || !field.appliesTo(request.getDocType())) continue;
            var old = field.scope() == AnswerScope.CASE
                    ? answerRepository.findByBusinessCaseIdAndScopeAndFieldKey(
                            business.getId(), AnswerScope.CASE, field.key())
                    : answerRepository.findBySecurityRequestIdAndFieldKey(id, field.key());
            if (old.isPresent()
                    && old.get().hasValue()
                    && !Objects.equals(
                            command.answerRevisions().get(field.key()),
                            old.get().getRevision()))
                throw new ConflictException("원본 답변이 변경되었습니다. 새로고침한 후 입력을 확인해 주세요.");
            answers.inputByStaff(
                    actor,
                    request,
                    field.key(),
                    entry.getValue(),
                    command.answerRevisions().get(field.key()));
        }
        plan.replace(json.writeValueAsString(command), actor.username(), clock.instant());
        repository.saveAndFlush(plan);
        events.publishEvent(new AnswerChangedEvent(
                actor,
                answers.unsubmittedRequestIds(business.getId()),
                "infrastructure.plan",
                answers.submittedRequestIds(business.getId())));
        audit.record(
                actor,
                AuditTargetType.SECURITY_REQUEST,
                String.valueOf(id),
                plan.getRevision(),
                AuditAction.ANSWER_STAFF_INPUT,
                null,
                "ICT인프라 검토 정보 저장 · 통신 구간 " + command.connections().size() + "개");
        return snapshot(id);
    }

    public void requireCurrent(SecurityRequest request) {
        var plan = snapshot(request.getId());
        if (plan.connections().isEmpty()) return;
        var first = plan.connections().getFirst();
        var expected = Map.of(
                "system.target_server",
                first.destinationSystem(),
                "network.source",
                first.sourceAddress(),
                "network.destination",
                first.destinationAddress(),
                "firewall.protocol",
                first.protocol(),
                "firewall.port",
                first.port(),
                "firewall.period",
                first.startDate() + " ~ " + first.endDate(),
                "firewall.purpose",
                first.purpose());
        for (var answer : answers.allAnswers(request)) {
            var value = expected.get(answer.fieldKey());
            if (value != null
                    && !value.strip()
                            .equals(Objects.toString(answer.value(), "").strip()))
                throw new DomainRuleViolationException(
                        "원본 답변과 ICT인프라 검토 정보가 달라졌습니다. ICT인프라 담당자가 협의 단계에서 통신 구간을 다시 저장해 주세요.");
        }
    }

    private void validate(String label, String type, String value) {
        AnswerFieldPolicy.validate(
                new FieldDefinition(
                        "infrastructure", label, AnswerScope.REQUEST, Set.of(), type, true, MemberRole.INFRA, ""),
                value);
    }
}
