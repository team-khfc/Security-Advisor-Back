package kr.securityadvisor.request.service;

import java.util.*;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.dto.ConsultationOverview.Field;

public final class ConsultationFields {
    private ConsultationFields() {}

    public static Field answer(String label, String group, AnswerResponse a) {
        return new Field(
                label,
                group,
                Objects.toString(a.value(), ""),
                a.confirmed() && !a.needsReview(),
                Objects.toString(a.evidence(), ""));
    }

    public static Map<String, Field> plan(InfrastructurePlanResponse p) {
        Map<String, Field> fields = new TreeMap<>();
        for (var s : p.servers()) {
            String group = "서버 · " + (s.name().isBlank() ? "이름 확인 필요" : s.name());
            Map<String, String> values = new LinkedHashMap<>();
            values.put("서버명", s.name());
            values.put("용도", s.purpose());
            values.put("운영 형태", s.environment());
            values.put("CPU", s.cpu());
            values.put("메모리", s.memory());
            values.put("저장공간", s.storage());
            values.put("OS", s.os());
            values.put("DB", s.database());
            values.put("IP·CIDR", s.address());
            values.put("망 구간", s.zone());
            values.forEach((label, value) -> fields.put(
                    "server:" + s.id() + ":" + label,
                    new Field(
                            label,
                            group,
                            value,
                            !p.draft() && (!s.aiInferred() || s.aiConfirmed()),
                            String.join("\n", s.evidence().values()))));
        }
        for (var c : p.connections()) {
            String group = "통신 · " + c.sourceSystem() + " → " + c.destinationSystem();
            Map<String, String> values = new LinkedHashMap<>();
            values.put("출발지", c.sourceSystem() + " / " + c.sourceAddress() + " / " + c.sourceZone());
            values.put("목적지", c.destinationSystem() + " / " + c.destinationAddress() + " / " + c.destinationZone());
            values.put("프로토콜", c.protocol());
            values.put("포트", c.port());
            values.put("허용 목적", c.purpose());
            values.put("방향", c.direction());
            values.put("허용 기간", c.startDate() + " ~ " + c.endDate());
            values.forEach((label, value) -> fields.put(
                    "connection:" + c.id() + ":" + label,
                    new Field(label, group, value, !p.draft() && !p.aiPending(), "")));
        }
        p.operations()
                .forEach((key, value) -> fields.put(
                        "operation:" + key,
                        new Field(
                                InfrastructurePlanValidation.OPERATIONS.getOrDefault(key, key),
                                "운영 조건",
                                value,
                                !p.draft(),
                                "")));
        p.checklist()
                .forEach((key, value) -> fields.put(
                        "check:" + key,
                        new Field(
                                InfrastructurePlanValidation.CHECKS.getOrDefault(key, key),
                                "검토 체크리스트",
                                value ? "확인함" : "확인 필요",
                                !p.draft() && value,
                                "")));
        if (!p.notes().isBlank()) fields.put("plan:notes", new Field("검토 사항", "운영 조건", p.notes(), !p.draft(), ""));
        return fields;
    }
}
