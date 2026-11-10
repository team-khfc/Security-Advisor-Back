package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ConsultationOverview(
        String fingerprint,
        boolean baselineAvailable,
        Instant baselineAt,
        String caseTitle,
        String department,
        Map<String, Field> baseline,
        Map<String, Field> current,
        List<Change> changes,
        Summary summary) {
    @io.swagger.v3.oas.annotations.media.Schema(name = "ConsultationField")
    public record Field(String label, String group, String value, boolean confirmed, String evidence) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "ConsultationChange")
    public record Change(
            long id,
            String kind,
            String actorName,
            String actorRole,
            Instant at,
            Map<String, Field> before,
            Map<String, Field> after) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "ConsultationSummary")
    public record Summary(String fingerprint, String model, Instant at, List<SummaryItem> items) {}

    @io.swagger.v3.oas.annotations.media.Schema(name = "ConsultationSummaryItem")
    public record SummaryItem(String text, List<String> evidenceIds) {}
}
