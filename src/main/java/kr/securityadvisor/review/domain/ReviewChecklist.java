package kr.securityadvisor.review.domain;

import java.util.List;
import java.util.Map;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.ReviewField;

/**
 * 분야별 필수 체크리스트(F-04). 협의 완료는 모든 항목이 확인(true)되어야 가능하다.
 * 항목 키는 데이터 계약이다 — 화면 문구는 프론트가 정한다.
 */
public final class ReviewChecklist {

    /** INFRA: 출발지·목적지·프로토콜·포트·기간. */
    public static final List<String> INFRA_ITEMS = List.of("source", "destination", "protocol", "port", "period");

    /** SECURITY: 처리 정보·외부 전송·권한·기록. */
    public static final List<String> SECURITY_ITEMS =
            List.of("processed_info", "external_transfer", "access_control", "logging");

    private ReviewChecklist() {}

    public static List<String> itemsOf(ReviewField field) {
        return field == ReviewField.INFRA ? INFRA_ITEMS : SECURITY_ITEMS;
    }

    /**
     * 체크 결과가 분야의 필수 항목을 전부 확인했는지 검사한다.
     *
     * @throws DomainRuleViolationException 빠지거나 확인되지 않은 항목이 있을 때
     */
    public static void requireAllChecked(ReviewField field, Map<String, Boolean> checklist) {
        List<String> missing = itemsOf(field).stream()
                .filter(item -> checklist == null || !Boolean.TRUE.equals(checklist.get(item)))
                .toList();
        if (!missing.isEmpty()) {
            throw new DomainRuleViolationException("확인되지 않은 체크리스트 항목이 있습니다: " + String.join(", ", missing));
        }
    }
}
