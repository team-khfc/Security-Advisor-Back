package kr.securityadvisor.request.service;

import java.time.LocalDate;
import java.util.*;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.*;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.request.domain.AnswerFieldPolicy;
import kr.securityadvisor.request.dto.InfrastructurePlanCommand;
import kr.securityadvisor.request.dto.InfrastructurePlanCommand.*;

/** 임시 저장은 미입력을 허용하되 잘못된 값·끊어진 서버 연결은 허용하지 않는다. */
public final class InfrastructurePlanValidation {
    public static final Map<String, String> CHECKS = Map.of(
            "serverEnvironment",
            "서버·DB 구성과 운영 환경",
            "network",
            "통신 구간과 망 분리",
            "management",
            "관리 접속 경로",
            "operations",
            "접속 기간과 운영 책임");
    public static final Map<String, String> OPERATIONS = Map.of(
            "network",
            "네트워크 구성·망 분리",
            "management",
            "관리 접속 경로",
            "owner",
            "운영 담당·책임",
            "backup",
            "백업·복구",
            "redundancy",
            "이중화",
            "monitoring",
            "모니터링");
    private static final Set<String> ZONES = Set.of("외부", "DMZ", "내부망", "클라우드", "기타");

    private InfrastructurePlanValidation() {}

    public static InfrastructurePlanCommand normalize(InfrastructurePlanCommand input) {
        Map<String, Server> servers = new LinkedHashMap<>();
        for (var server : input.servers()) {
            if (servers.putIfAbsent(server.id(), server) != null) fail("서버 식별자가 중복되었습니다.");
            optional("서버 IP", "IP_ADDRESS", server.address(), input.draft());
            choice(server.zone(), ZONES, input.draft(), "서버 망 구간");
            choice(server.environment(), Set.of("물리", "가상", "클라우드", "외부", "기타"), input.draft(), "서버 운영 형태");
            if (!input.draft()) {
                required(server.name(), "서버·시스템명");
                required(server.purpose(), "서버 용도");
                if (server.aiInferred() && !server.aiConfirmed()) fail("AI 서버 사양의 근거와 값을 확인해 주세요.");
            }
        }
        if (!OPERATIONS.keySet().containsAll(input.operations().keySet())
                || !CHECKS.keySet().containsAll(input.checklist().keySet())) fail("알 수 없는 운영·체크리스트 항목입니다.");
        if (!input.draft()) {
            if (input.aiPending()) fail("AI 제안을 확인한 후 저장해 주세요.");
            if (input.connections().isEmpty()) fail("통신 구간을 하나 이상 입력해 주세요.");
            if (input.schemaVersion() >= 2) {
                if (servers.isEmpty()) fail("서버를 하나 이상 등록해 주세요.");
                for (String key : List.of("network", "management", "owner"))
                    required(input.operations().get(key), OPERATIONS.get(key));
                CHECKS.forEach((key, label) -> {
                    if (!Boolean.TRUE.equals(input.checklist().get(key))) fail(label + " 확인이 필요합니다.");
                });
            }
        }
        Set<String> ids = new HashSet<>();
        List<Connection> connections = new ArrayList<>();
        for (var row : input.connections()) {
            if (!ids.add(row.id())) fail("통신 구간 식별자가 중복되었습니다.");
            Server source = linked(servers, row.sourceServerId()), dest = linked(servers, row.destinationServerId());
            var c = new Connection(
                    row.id(),
                    source == null ? row.sourceSystem() : source.name(),
                    source == null ? row.sourceAddress() : source.address(),
                    source == null ? row.sourceZone() : source.zone(),
                    dest == null ? row.destinationSystem() : dest.name(),
                    dest == null ? row.destinationAddress() : dest.address(),
                    dest == null ? row.destinationZone() : dest.zone(),
                    row.protocol(),
                    row.port(),
                    row.purpose(),
                    row.direction(),
                    row.startDate(),
                    row.endDate(),
                    row.sourceServerId(),
                    row.destinationServerId());
            optional("출발지 주소", "IP_ADDRESS", c.sourceAddress(), input.draft());
            optional("목적지 주소", "IP_ADDRESS", c.destinationAddress(), input.draft());
            optional("프로토콜", "PROTOCOL", c.protocol(), input.draft());
            optional("포트", "PORT", c.port(), input.draft());
            choice(c.sourceZone(), ZONES, input.draft(), "출발지 망 구간");
            choice(c.destinationZone(), ZONES, input.draft(), "목적지 망 구간");
            choice(c.direction(), Set.of("인바운드", "아웃바운드", "양방향"), input.draft(), "통신 방향");
            for (String date : List.of(c.startDate(), c.endDate())) {
                if (date.isBlank() && input.draft()) continue;
                try {
                    LocalDate.parse(date);
                } catch (RuntimeException e) {
                    fail("허용 기간 날짜를 확인해 주세요.");
                }
            }
            if (!c.startDate().isBlank()
                    && !c.endDate().isBlank()
                    && c.endDate().compareTo(c.startDate()) < 0) fail("종료일은 시작일보다 빠를 수 없습니다.");
            if (!input.draft()) {
                required(c.sourceSystem(), "출발지 서버명");
                required(c.destinationSystem(), "목적지 서버명");
                required(c.purpose(), "허용 목적");
            }
            connections.add(c);
        }
        return new InfrastructurePlanCommand(
                input.expectedRevision(),
                connections,
                input.notes(),
                input.answerRevisions(),
                input.schemaVersion(),
                input.draft(),
                input.aiPending(),
                input.servers(),
                input.operations(),
                input.checklist());
    }

    private static Server linked(Map<String, Server> servers, String id) {
        if (id == null || id.isBlank()) return null;
        if (!servers.containsKey(id)) fail("연결된 서버를 찾을 수 없습니다. 통신 구간 연결을 해제하거나 서버를 다시 선택해 주세요.");
        return servers.get(id);
    }

    private static void optional(String label, String type, String value, boolean draft) {
        if (draft && value.isBlank()) return;
        AnswerFieldPolicy.validate(
                new FieldDefinition(
                        "infrastructure", label, AnswerScope.REQUEST, Set.of(), type, true, MemberRole.INFRA, ""),
                value);
    }

    private static void choice(String value, Set<String> choices, boolean draft, String label) {
        if (draft && value.isBlank()) return;
        if (!choices.contains(value)) fail(label + "을 선택해 주세요.");
    }

    private static void required(String value, String label) {
        if (value == null || value.isBlank()) fail(label + "을 입력해 주세요.");
    }

    private static void fail(String message) {
        throw new DomainRuleViolationException(message);
    }
}
