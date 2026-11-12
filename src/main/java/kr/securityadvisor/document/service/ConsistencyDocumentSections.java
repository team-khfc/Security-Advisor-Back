package kr.securityadvisor.document.service;

import java.util.*;
import kr.securityadvisor.document.dto.*;
import kr.securityadvisor.request.dto.ConsistencyReviewResponse;

/** 제출 당시의 최근 검사 기록을 출력하며 사람의 판단과 AI 후보를 구분한다. */
public final class ConsistencyDocumentSections {
    private ConsistencyDocumentSections() {}

    public static List<DocumentSection> from(List<ConsistencyReviewResponse> records) {
        var values = new ArrayList<DocumentValue>();
        values.add(value(
                "notice",
                "기록 범위",
                records == null
                        ? "이 제출본에는 모순 검사 기록이 저장되지 않았습니다. 과거 기록을 추정하지 않습니다."
                        : "문서 생성 기준의 검사·처리 이력입니다. 검사 미실시·후보 없음은 사실의 정확성을 보장하지 않습니다."));
        if (records != null)
            for (var record : records) {
                values.add(value(
                        "run" + record.id(),
                        "검사 #" + record.id(),
                        record.result().checkedAt() + " · " + record.result().model() + " · 문서 "
                                + record.result().sources().size() + "개 · "
                                + (record.stale() ? "입력 변경 후 재검사 필요" : "검사 당시 입력 일치")));
                for (int i = 0; i < record.result().findings().size(); i++) {
                    var finding = record.result().findings().get(i);
                    final int index = i;
                    var latest = record.decisions().stream()
                            .filter(d -> d.findingIndex() == index)
                            .reduce((a, b) -> b)
                            .orElse(null);
                    String status = latest == null
                            ? "확인 필요"
                            : switch (latest.status()) {
                                case "RESOLVED" -> "확인·해소";
                                case "DISMISSED" -> "모순 아님";
                                case "SUPPLEMENT_REQUESTED" -> "보완 요청";
                                default -> "확인 필요";
                            };
                    values.add(value(
                            record.id() + "." + i,
                            finding.label(),
                            "AI 모순 후보 · " + status + "\n검사 당시 값: " + finding.answer()
                                    + "\n원문 파일 #" + finding.fileId() + ": " + finding.quote() + "\n"
                                    + finding.explanation()
                                    + (latest == null
                                            ? ""
                                            : "\n담당자 기록: " + latest.actor() + " · " + latest.at() + " · "
                                                    + latest.reason())));
                }
            }
        return List.of(new DocumentSection("모순 후보 검사·처리 기록", values));
    }

    private static DocumentValue value(String key, String label, String text) {
        return new DocumentValue(
                "preparation.consistency." + key, label, "REQUEST", text, null, true, false, text, "검사 기록 · 승인 아님");
    }
}
