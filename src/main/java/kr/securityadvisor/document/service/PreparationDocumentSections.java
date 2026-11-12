package kr.securityadvisor.document.service;

import java.util.*;
import kr.securityadvisor.document.dto.*;
import kr.securityadvisor.request.dto.PreparationReviewResponse;

/** 준비자료의 확인 여부는 서류 승인과 구분하며 제출본의 고정된 기록만 출력한다. */
public final class PreparationDocumentSections {
    private PreparationDocumentSections() {}

    public static List<DocumentSection> from(PreparationReviewResponse snapshot) {
        if (snapshot == null)
            return List.of(new DocumentSection(
                    "준비자료 검토 기록",
                    List.of(value("legacy", "기록 범위", "이 제출본에는 준비자료 검토 기록이 없습니다. 현재 자료로 과거 기록을 추정하지 않습니다."))));
        var values = new ArrayList<DocumentValue>();
        values.add(value("notice", "안내", "초안은 조회 당시, 제출본은 제출 당시의 준비자료 현황입니다. 권고 자료이며 미첨부만으로 제출을 차단하지 않습니다."));
        for (var item : snapshot.items()) {
            String status = "NOT_APPLICABLE".equals(item.applicability())
                    ? "현재 조건 미해당"
                    : "UNKNOWN".equals(item.applicability())
                            ? "사업 조건 확인 필요"
                            : Map.of(
                                            "MISSING",
                                            "미첨부",
                                            "IN_REVIEW",
                                            "검토 중",
                                            "NEEDS_SUPPLEMENT",
                                            "보완 필요",
                                            "CONFIRMED",
                                            "확인 완료")
                                    .get(item.status());
            String text = status + (item.stale() ? " · 조건 변경 후 재검토 필요" : "")
                    + "\n검토 분야: " + ("INFRA".equals(item.reviewer()) ? "ICT인프라" : "정보보안")
                    + "\n첨부: "
                    + (item.files().isEmpty()
                            ? "없음"
                            : item.files().stream()
                                    .map(f -> f.name() + " (#" + f.fileId() + ")")
                                    .collect(java.util.stream.Collectors.joining(", ")))
                    + "\n최근 기록: " + (item.actor() == null ? "없음" : item.actor() + " · " + item.reviewedAt())
                    + (item.stale() ? " (이전 조건의 기록)" : "") + "\n사유: " + item.note();
            values.add(value(item.key(), item.label(), text));
        }
        return List.of(new DocumentSection("준비자료 및 검토 결과", values));
    }

    private static DocumentValue value(String key, String label, String text) {
        return new DocumentValue(
                "preparation." + key, label, "REQUEST", text, null, true, false, text, "자료 현황 기록 · 승인 아님");
    }
}
