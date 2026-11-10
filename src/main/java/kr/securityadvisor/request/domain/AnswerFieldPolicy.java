package kr.securityadvisor.request.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.security.MemberRole;

/** 게시본의 작성 역할과 값 형식을 모든 답변 경로에 동일하게 적용한다. */
public final class AnswerFieldPolicy {
    private AnswerFieldPolicy() {}

    /** 명시적 미해당을 기존 문서 표시 값으로 정규화하되 필수 여부와 모순 입력을 검증한다. */
    public static String normalize(FieldDefinition field, String value, boolean notApplicable) {
        if (notApplicable) {
            if (field.required()) invalid(field, "필수 항목은 미해당으로 저장할 수 없습니다.");
            if (value != null && !value.isBlank() && !value.strip().equals("미해당"))
                invalid(field, "미해당 상태와 답변 값을 함께 저장할 수 없습니다.");
            return "미해당";
        }
        validate(field, value);
        return value.strip();
    }

    /** 입력은 항목 소유 역할만 가능하며 관리자에게 우회 권한을 주지 않는다. */
    public static void requireWrite(FieldDefinition field, MemberRole role) {
        if (role == MemberRole.ADMIN || field.askRole() != role) {
            throw new ForbiddenException(field.label() + ": 해당 항목의 작성 담당자만 입력할 수 있습니다.");
        }
    }

    /** 배정된 담당자는 신청자 답변을 확인할 수 있으나 다른 담당 분야를 대신 확인하지 않는다. */
    public static void requireConfirm(FieldDefinition field, MemberRole role) {
        if (field.askRole() == MemberRole.APPLICANT && (role == MemberRole.INFRA || role == MemberRole.SECURITY))
            return;
        requireWrite(field, role);
    }

    /** 값은 정규 형식으로 제출해야 하며 잘못된 기존 값도 확인으로 승인할 수 없다. */
    public static void validate(FieldDefinition field, String value) {
        if (value == null || value.isBlank() || value.length() > 4000) {
            invalid(field, "1~4000자의 답변을 입력해 주세요.");
        }
        String trimmed = value.strip();
        // 기존 미해당 표현과 호환하되 필수 항목을 우회하는 데 사용하지 않는다.
        if (trimmed.equals("미해당")) {
            if (field.required()) invalid(field, "필수 항목은 미해당으로 저장할 수 없습니다.");
            return;
        }
        switch (field.type()) {
            case "BOOLEAN" -> {
                if (!Set.of("true", "false", "예", "아니요").contains(trimmed))
                    invalid(field, "예(true) 또는 아니요(false)를 선택해 주세요.");
            }
            case "NUMBER" -> {
                if (!trimmed.matches("-?[0-9]+(?:\\.[0-9]+)?")) invalid(field, "숫자를 입력해 주세요.");
            }
            case "DATE_RANGE" -> {
                trimmed = trimmed.replaceFirst("~\\s*(상시|종료일 없음)$", "~");
                if (!trimmed.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}\\s*~\\s*(?:[0-9]{4}-[0-9]{2}-[0-9]{2})?")) {
                    invalid(field, "YYYY-MM-DD~YYYY-MM-DD 형식으로 입력해 주세요. 상시는 종료일을 비웁니다.");
                }
                String[] dates = trimmed.split("~", -1);
                try {
                    LocalDate start = LocalDate.parse(dates[0].strip());
                    if (!dates[1].isBlank() && LocalDate.parse(dates[1].strip()).isBefore(start)) {
                        invalid(field, "종료일은 시작일보다 빠를 수 없습니다.");
                    }
                } catch (DateTimeParseException e) {
                    invalid(field, "실제 달력에 있는 날짜를 입력해 주세요.");
                }
            }
            case "IP_ADDRESS", "PORT", "PROTOCOL" -> NetworkAnswerFormat.validate(field, trimmed);
            case "TEXT", "LONG_TEXT" -> {}
            default -> invalid(field, "지원하지 않는 항목 형식입니다.");
        }
    }

    private static void invalid(FieldDefinition field, String message) {
        throw new DomainRuleViolationException(field.label() + ": " + message);
    }
}
