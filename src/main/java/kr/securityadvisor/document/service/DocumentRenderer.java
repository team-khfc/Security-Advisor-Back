package kr.securityadvisor.document.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.document.domain.DocumentKind;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.dto.DocumentValue;
import kr.securityadvisor.request.domain.SecurityRequest;
import org.springframework.stereotype.Component;

/**
 * 서류 렌더링(F-06): 게시본 스키마 순서대로 공통/서류별 섹션을 만들고, 값마다 출처·확인 상태를 표기한다.
 * 미확정 값은 숨기지 않고 "담당자 확인 필요"로 드러낸다. 1차 범위 출력 형식은 Markdown.
 */
@Component
public class DocumentRenderer {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    /** 렌더링에 쓰는 값 한 건(현재 답변 또는 제출본 스냅샷에서 온다). */
    public record Source(String value, ValueSource valueSource, boolean confirmed, boolean needsReview) {}

    public List<DocumentSection> sections(FieldSchema schema, SecurityRequest request, Map<String, Source> sources) {
        return List.of(
                new DocumentSection(
                        "공통 정보(사업 건)",
                        values(
                                schema.caseFields().stream()
                                        .filter(f -> f.appliesTo(request.getDocType()))
                                        .toList(),
                                sources)),
                new DocumentSection("서류별 정보", values(schema.requestFields(request.getDocType()), sources)));
    }

    public int unconfirmedCount(List<DocumentSection> sections) {
        return (int) sections.stream()
                .flatMap(s -> s.values().stream())
                .filter(v -> !v.confirmed())
                .count();
    }

    public String markdown(
            String institution,
            String docTypeName,
            String caseTitle,
            DocumentKind kind,
            SecurityRequest request,
            List<DocumentSection> sections,
            String aiSupplement,
            String modelReference,
            Instant generatedAt) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(docTypeName).append(" — ").append(caseTitle).append("\n\n");
        md.append("- 기관: ").append(institution).append('\n');
        md.append("- 문서 구분: ").append(kind.label()).append(" (서류 확정 ≠ 방화벽 적용)\n");
        md.append("- 신청: #")
                .append(request.getId())
                .append(" · ")
                .append(request.getStage())
                .append("단계 ")
                .append(request.getStatus().label())
                .append('\n');
        md.append("- 생성: ").append(STAMP.format(generatedAt)).append(" (Asia/Seoul)\n\n");
        for (DocumentSection section : sections) {
            md.append("## ").append(section.title()).append("\n\n");
            md.append("| 항목 | 값 | 출처 | 상태 |\n|---|---|---|---|\n");
            for (DocumentValue v : section.values()) {
                md.append("| ")
                        .append(escape(v.label()))
                        .append(" | ")
                        .append(escape(v.display()))
                        .append(" | ")
                        .append(sourceLabel(v.valueSource()))
                        .append(" | ")
                        .append(v.status())
                        .append(" |\n");
            }
            md.append('\n');
        }
        if (aiSupplement != null && !aiSupplement.isBlank()) {
            md.append("## AI 보완 서술 (미확정 — 참고용)\n\n")
                    .append("> 아래 문장은 AI(")
                    .append(modelReference)
                    .append(")가 위 값을 바탕으로 작성한 제안이며, 사용자·담당자가 확인하기 전까지 서류의 값이 아니다.\n\n")
                    .append(aiSupplement.strip())
                    .append("\n\n");
        }
        md.append("---\n출처 범례: 사용자 답변 / AI 제안(근거 있음, 확인 전 미확정) / 담당자 입력 / 담당자 확인. "
                + "\"담당자 확인 필요\"는 값이 없거나 출처가 없는 항목이다.\n");
        return md.toString();
    }

    private static List<DocumentValue> values(List<FieldDefinition> fields, Map<String, Source> sources) {
        List<DocumentValue> values = new ArrayList<>();
        for (FieldDefinition field : fields) {
            if (!field.isApplicable(
                    key -> sources.containsKey(key) ? sources.get(key).value() : null)) continue;
            Source s = sources.get(field.key());
            boolean hasValue = s != null && s.value() != null && !s.value().isBlank();
            boolean confirmed = hasValue && s.valueSource() != null && s.confirmed();
            String display;
            String status;
            if (!hasValue) {
                display = field.isStaffField() ? "담당자 확인 필요" : "미입력";
                status = field.required() ? "필수 · 미입력" : "선택 · 미입력";
            } else if (s.valueSource() == null) {
                display = displayValue(field, s.value()) + " (담당자 확인 필요)";
                status = "출처 없음 · 담당자 확인 필요";
            } else if (!confirmed) {
                display = displayValue(field, s.value());
                status = "미확정 · " + (s.valueSource() == ValueSource.AI_INFERRED ? "AI 제안 확인 전" : "확인 전");
            } else {
                display = displayValue(field, s.value());
                status = "확인 완료";
            }
            if (s != null && s.needsReview()) {
                status = "재검토 필요 · " + status;
            }
            values.add(new DocumentValue(
                    field.key(),
                    field.label(),
                    field.scope().name(),
                    hasValue ? s.value() : null,
                    s == null || s.valueSource() == null
                            ? null
                            : s.valueSource().name(),
                    confirmed,
                    s != null && s.needsReview(),
                    display,
                    status));
        }
        return values;
    }

    static String displayValue(FieldDefinition field, String value) {
        if ("BOOLEAN".equals(field.type())) {
            if ("true".equals(value)) return "예";
            if ("false".equals(value)) return "아니요";
        }
        return value;
    }

    static String sourceLabel(String valueSource) {
        if (valueSource == null) {
            return "없음";
        }
        return switch (ValueSource.valueOf(valueSource)) {
            case USER_ANSWER -> "사용자 답변";
            case AI_INFERRED -> "AI 제안";
            case STAFF_INPUT -> "담당자 입력";
            case STAFF_CONFIRMED -> "담당자 확인";
        };
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}
