package kr.securityadvisor.document.service;

import java.util.*;
import kr.securityadvisor.document.dto.*;
import kr.securityadvisor.request.dto.InfrastructurePlanResponse;
import kr.securityadvisor.request.service.InfrastructurePlanValidation;

/** 임시 저장·AI 확인 상태를 문서에서도 확정값과 구분한다. */
public final class InfrastructureDocumentSections {
    private InfrastructureDocumentSections() {}

    public static List<DocumentSection> from(InfrastructurePlanResponse plan) {
        if (plan == null) return List.of();
        List<DocumentSection> result = new ArrayList<>();
        int index = 0;
        for (var s : plan.servers()) {
            String key = "infrastructure.server." + (++index);
            boolean confirmed = !plan.draft() && (!s.aiInferred() || s.aiConfirmed());
            String source = s.aiInferred() ? (s.aiConfirmed() ? "STAFF_CONFIRMED" : "AI_INFERRED") : "STAFF_INPUT";
            List<DocumentValue> values = new ArrayList<>();
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("서버명", s.name());
            fields.put("용도", s.purpose());
            fields.put("운영 형태", s.environment());
            fields.put("CPU", s.cpu());
            fields.put("메모리", s.memory());
            fields.put("저장공간", s.storage());
            fields.put("OS", s.os());
            fields.put("DB", s.database());
            fields.put("IP·CIDR", s.address());
            fields.put("망 구간", s.zone());
            fields.forEach(
                    (label, value) -> values.add(value(key + "." + label, label, value, source, confirmed, false)));
            if (!s.evidence().isEmpty())
                values.add(value(
                        key + ".evidence",
                        "AI 제안 근거",
                        String.join("\n", s.evidence().values()),
                        source,
                        confirmed,
                        false));
            result.add(new DocumentSection("서버 구성 " + index, values));
        }
        index = 0;
        for (var row : plan.connections()) {
            String text = show(row.sourceSystem()) + " (" + show(row.sourceZone()) + ", " + show(row.sourceAddress())
                    + ") → "
                    + show(row.destinationSystem()) + " (" + show(row.destinationZone()) + ", "
                    + show(row.destinationAddress())
                    + ") · " + show(row.direction()) + " · " + show(row.protocol()) + " / " + show(row.port()) + " · "
                    + show(row.startDate()) + " ~ " + show(row.endDate()) + " · 목적: " + show(row.purpose());
            result.add(new DocumentSection(
                    "통신 구간 " + (++index),
                    List.of(value(
                            "infrastructure.connection." + index,
                            "서버·네트워크 구성",
                            text,
                            plan.aiPending() ? "AI_INFERRED" : "STAFF_INPUT",
                            !plan.draft() && !plan.aiPending(),
                            false))));
        }
        List<DocumentValue> operations = new ArrayList<>();
        InfrastructurePlanValidation.OPERATIONS.forEach((key, label) -> {
            if (plan.schemaVersion() >= 2)
                operations.add(value(
                        "infrastructure.operation." + key,
                        label,
                        plan.operations().getOrDefault(key, ""),
                        "STAFF_INPUT",
                        !plan.draft(),
                        true));
        });
        if (plan.schemaVersion() >= 2)
            InfrastructurePlanValidation.CHECKS.forEach((key, label) -> operations.add(value(
                    "infrastructure.check." + key,
                    label,
                    Boolean.TRUE.equals(plan.checklist().get(key)) ? "확인함" : "확인 필요",
                    "STAFF_INPUT",
                    !plan.draft() && Boolean.TRUE.equals(plan.checklist().get(key)),
                    false)));
        if (!plan.notes().isBlank())
            operations.add(value("infrastructure.notes", "검토 사항", plan.notes(), "STAFF_INPUT", !plan.draft(), false));
        if (!operations.isEmpty())
            result.add(new DocumentSection("ICT인프라 운영 및 검토 사항" + (plan.draft() ? " · 임시 저장" : ""), operations));
        return result;
    }

    private static String show(String text) {
        return text == null || text.isBlank() ? "확인 필요" : text;
    }

    private static DocumentValue value(
            String key, String label, String value, String source, boolean confirmed, boolean optional) {
        String display = value == null || value.isBlank() ? (optional ? "입력 안 함(선택)" : "담당자 확인 필요") : value;
        return new DocumentValue(
                key,
                label,
                "REQUEST",
                value,
                source,
                confirmed && (optional || (value != null && !value.isBlank())),
                false,
                display,
                confirmed ? (source.equals("STAFF_CONFIRMED") ? "AI 추론 · 담당자 확인" : "담당자 확인") : "확인 필요 · 임시 저장");
    }
}
