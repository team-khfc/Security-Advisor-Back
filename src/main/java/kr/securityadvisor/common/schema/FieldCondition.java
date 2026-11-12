package kr.securityadvisor.common.schema;

import java.util.function.Function;

/** 사업 공통 예·아니요 답변으로 질문의 적용 여부를 결정한다. 답변 확정 여부는 제출 검사가 별도로 검증한다. */
public record FieldCondition(String fieldKey, boolean expectedValue) {
    public boolean matches(Function<String, String> values) {
        String value = values.apply(fieldKey);
        if (value == null) return false;
        return expectedValue ? "true".equals(value) || "예".equals(value) : "false".equals(value) || "아니요".equals(value);
    }
}
