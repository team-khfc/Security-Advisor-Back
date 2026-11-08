package kr.securityadvisor.review.dto;

import java.util.Locale;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.request.domain.RequestStatus;
import kr.securityadvisor.review.domain.ReviewStatus;

/**
 * 담당자 요청함 조회 조건(F-04). 모두 선택이며 AND 로 결합한다.
 *
 * @param status 협의 상태
 * @param requestStatus 신청 상태
 * @param field 협의 분야
 * @param docType 서류 유형
 * @param keyword 사업 건 제목·신청자 아이디·신청 번호에 대한 부분 일치(대소문자 무시)
 */
public record ReviewInboxFilter(
        ReviewStatus status, RequestStatus requestStatus, ReviewField field, DocTypeCode docType, String keyword) {

    public static final ReviewInboxFilter NONE = new ReviewInboxFilter(null, null, null, null, null);

    /**
     * 질의 문자열에서 만든다. 상태 이름·서류 유형 코드 형식이 틀리면 400.
     *
     * @throws BadRequestException 알 수 없는 상태 이름
     */
    public static ReviewInboxFilter of(
            String status, String requestStatus, ReviewField field, String docType, String keyword) {
        DocTypeCode docTypeCode;
        try {
            docTypeCode = docType == null || docType.isBlank() ? null : DocTypeCode.of(docType);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("docType 값이 올바르지 않습니다: " + docType);
        }
        return new ReviewInboxFilter(
                parse(ReviewStatus.class, status, "status"),
                parse(RequestStatus.class, requestStatus, "requestStatus"),
                field,
                docTypeCode,
                keyword);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(name + " 값이 올바르지 않습니다: " + value);
        }
    }

    public boolean matches(ReviewInboxItem item) {
        if (status != null && item.review().status() != status) {
            return false;
        }
        if (requestStatus != null && item.request().status() != requestStatus) {
            return false;
        }
        if (field != null && item.review().field() != field) {
            return false;
        }
        if (docType != null && !docType.equals(item.request().docType())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.strip().toLowerCase(Locale.ROOT);
        return item.caseTitle().toLowerCase(Locale.ROOT).contains(needle)
                || item.applicantUsername().toLowerCase(Locale.ROOT).contains(needle)
                || String.valueOf(item.request().securityRequestId()).equals(needle);
    }
}
