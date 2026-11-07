package kr.securityadvisor.common.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;

/**
 * 서류 유형 코드(값 객체). 어떤 유형이 있는지는 코드가 아니라 게시본의 {@code docTypes} 데이터가 정한다(ADR 0005).
 *
 * <p>코드는 한 번 게시되면 바꾸지 않는다 — 신청·제출본·방화벽 규칙이 이 문자열로 유형을 참조한다. 이름·양식은 새 게시본에서 바뀐다.
 * JSON 에서는 {@code "FIREWALL"} 처럼 문자열 하나로 오간다.
 *
 * @param code 대문자·숫자·밑줄, 2~30자(예: FIREWALL, SECURITY_REVIEW, USB_REQUEST)
 */
public record DocTypeCode(String code) implements Comparable<DocTypeCode> {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    public DocTypeCode {
        if (code == null || !FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("서류 유형 코드 형식이 올바르지 않습니다: " + code);
        }
    }

    @JsonCreator
    public static DocTypeCode of(String code) {
        return new DocTypeCode(code == null ? null : code.strip().toUpperCase());
    }

    @JsonValue
    public String code() {
        return code;
    }

    @Override
    public int compareTo(DocTypeCode other) {
        return code.compareTo(other.code);
    }

    @Override
    public String toString() {
        return code;
    }
}
