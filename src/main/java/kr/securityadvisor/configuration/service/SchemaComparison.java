package kr.securityadvisor.configuration.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.configuration.dto.ConfigurationComparisonResponse.Change;

/** 저장 키를 기준으로 비교하여 질문 순서 변경을 문구 변경으로 잘못 표시하지 않는다. */
public final class SchemaComparison {
    private SchemaComparison() {}

    public static List<Change> compare(FieldSchema before, FieldSchema after) {
        List<Change> changes = new ArrayList<>();
        add(
                changes,
                "firewallPolicy.reviewMonths",
                "상시 방화벽 재검토 주기(개월)",
                before.firewallPolicy().reviewMonths(),
                after.firewallPolicy().reviewMonths(),
                false);
        add(
                changes,
                "firewallPolicy.reviewNoticeDays",
                "방화벽 재검토 알림(달력일 전)",
                before.firewallPolicy().reviewNoticeDays(),
                after.firewallPolicy().reviewNoticeDays(),
                false);
        var a = before.deadlinePolicy();
        var b = after.deadlinePolicy();
        add(
                changes,
                "deadlinePolicy.reviewBusinessDays",
                "분야별 협의 기한(영업일)",
                a.reviewBusinessDays(),
                b.reviewBusinessDays(),
                false);
        add(
                changes,
                "deadlinePolicy.supplementBusinessDays",
                "보완 응답 기한(영업일)",
                a.supplementBusinessDays(),
                b.supplementBusinessDays(),
                false);
        add(
                changes,
                "deadlinePolicy.finalConfirmationBusinessDays",
                "최종 확정 기한(영업일)",
                a.finalConfirmationBusinessDays(),
                b.finalConfirmationBusinessDays(),
                false);
        add(
                changes,
                "deadlinePolicy.imminentBusinessDays",
                "기한 임박 알림(영업일 전)",
                a.imminentBusinessDays(),
                b.imminentBusinessDays(),
                false);
        add(changes, "deadlinePolicy.holidays", "기관 공휴일", a.holidays(), b.holidays(), false);
        Map<String, FieldDefinition> old = new LinkedHashMap<>();
        before.fields().forEach(f -> old.put(f.key(), f));
        Map<String, FieldDefinition> current = new LinkedHashMap<>();
        after.fields().forEach(f -> current.put(f.key(), f));
        old.forEach((key, field) -> {
            if (!current.containsKey(key)) add(changes, "fields." + key, "질문 삭제", describe(field, before), null, true);
        });
        current.forEach((key, field) -> {
            FieldDefinition prior = old.get(key);
            String path = "fields." + key;
            if (prior == null) {
                add(changes, path, "질문 추가", null, describe(field, after), true);
            } else {
                add(changes, path + ".label", key + " · 질문 문구", prior.label(), field.label(), false);
                add(changes, path + ".hint", key + " · 도움말", prior.hint(), field.hint(), false);
                add(changes, path + ".condition", key + " · 사업 적용 조건", prior.condition(), field.condition(), true);
                add(changes, path + ".scope", key + " · 답변 범위", prior.scope(), field.scope(), true);
                add(changes, path + ".docTypes", key + " · 적용 서류", prior.docTypes(), field.docTypes(), true);
                add(changes, path + ".type", key + " · 응답 형식", prior.type(), field.type(), true);
                add(changes, path + ".required", key + " · 필수 여부", prior.required(), field.required(), false);
                add(changes, path + ".askRole", key + " · 작성 역할", prior.askRole(), field.askRole(), true);
            }
        });
        add(
                changes,
                "fields.order",
                "질문 순서",
                before.fields().stream()
                        .map(FieldDefinition::key)
                        .filter(current::containsKey)
                        .toList(),
                after.fields().stream()
                        .map(FieldDefinition::key)
                        .filter(old::containsKey)
                        .toList(),
                false);
        add(changes, "docTypes", "서류 유형 설정", before.docTypes(), after.docTypes(), true);
        return List.copyOf(changes);
    }

    private static void add(
            List<Change> changes, String path, String label, Object before, Object after, boolean contract) {
        if (!Objects.equals(before, after))
            changes.add(new Change(path, label, display(before), display(after), contract));
    }

    private static String display(Object value) {
        if (value == null) return "없음";
        if (value instanceof Set<?> values)
            return values.stream().map(String::valueOf).sorted().toList().toString();
        return String.valueOf(value);
    }

    private static String describe(FieldDefinition field, FieldSchema schema) {
        String role = switch (field.askRole()) {
            case APPLICANT -> "신청자";
            case INFRA -> "ICT인프라";
            case SECURITY -> "정보보안";
            case ADMIN -> "기관 관리자";
        };
        String type = switch (field.type()) {
            case "TEXT" -> "단답형";
            case "LONG_TEXT" -> "장문형";
            case "BOOLEAN" -> "선택형(예·아니요)";
            case "DATE_RANGE" -> "기간";
            case "NUMBER" -> "숫자";
            case "IP_ADDRESS" -> "IP 주소 / CIDR";
            case "PORT" -> "포트 / 범위";
            case "PROTOCOL" -> "프로토콜";
            default -> field.type();
        };
        return field.label() + "\n저장 키: " + field.key() + "\n응답 형식: " + type + "\n작성 역할: " + role
                + "\n범위: "
                + (field.scope() == kr.securityadvisor.common.schema.AnswerScope.CASE ? "사업 공통 정보" : "서류별 정보")
                + "\n적용 서류: "
                + schema.docTypes().stream()
                        .filter(d -> field.appliesTo(d.code()))
                        .map(d -> d.name())
                        .toList()
                + "\n필수 여부: " + (field.required() ? "필수" : "선택") + "\n도움말: "
                + (field.hint() == null ? "없음" : field.hint())
                + "\n적용 조건: "
                + (field.condition() == null
                        ? "항상 질문"
                        : field.condition().fieldKey() + " = "
                                + (field.condition().expectedValue() ? "예" : "아니요"));
    }
}
