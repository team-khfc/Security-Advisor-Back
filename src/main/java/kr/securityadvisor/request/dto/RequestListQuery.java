package kr.securityadvisor.request.dto;

import java.time.LocalDate;
import java.util.Set;
import kr.securityadvisor.common.error.BadRequestException;

/** 화면 검색 조건. 허용된 정렬만 SQL 상수로 변환하고 모든 검색값은 바인딩한다. */
public record RequestListQuery(
        String query,
        String status,
        String docType,
        Long businessCaseId,
        LocalDate from,
        LocalDate to,
        String inbox,
        String documentKind,
        String supplementState,
        String sort,
        int page,
        int size) {
    public RequestListQuery {
        query = query == null ? "" : query.strip();
        inbox = inbox == null ? "all" : inbox;
        documentKind = documentKind == null ? "all" : documentKind;
        supplementState = supplementState == null ? "all" : supplementState;
        sort = sort == null ? "createdAt,desc" : sort;
        if (query.length() > 200
                || page < 0
                || size < 1
                || size > 100
                || !Set.of("all", "mine", "staff", "done").contains(inbox)
                || !Set.of("all", "draft", "submitted", "finalized").contains(documentKind)
                || !Set.of("all", "pending", "resolved", "any").contains(supplementState)
                || !Set.of(
                                "createdAt,desc",
                                "createdAt,asc",
                                "updatedAt,desc",
                                "updatedAt,asc",
                                "id,asc",
                                "id,desc",
                                "dueDate,asc")
                        .contains(sort)
                || (from != null && to != null && from.isAfter(to))) {
            throw new BadRequestException("검색 조건이나 페이지 값이 올바르지 않습니다");
        }
    }
}
