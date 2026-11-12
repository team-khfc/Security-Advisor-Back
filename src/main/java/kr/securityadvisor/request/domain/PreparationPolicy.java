package kr.securityadvisor.request.domain;

import java.util.List;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/** 권고 준비자료 목록. 법적 의무의 자동 판정이나 제출 요건을 대신하지 않는다. */
public final class PreparationPolicy {
    private PreparationPolicy() {}

    public record Requirement(String key, String label, String reviewer, String condition, String description) {}

    public static final List<Requirement> REQUIREMENTS = List.of(
            new Requirement("plan", "사업계획서", "SECURITY", "", "목적·이용자·처리 정보·사업 범위·일정"),
            new Requirement("rfp", "제안요청서·과업 범위 자료", "SECURITY", "", "기능·보안 요구사항과 수행 범위. 발주가 없는 경우 업무 범위 설명 자료"),
            new Requirement("architecture", "시스템·망 구성도", "INFRA", "", "서버·DB·망 구간·외부 연결과 변경 전후 구성"),
            new Requirement("safeguards", "자체 보안대책·점검표", "SECURITY", "", "관리적·기술적·물리적 보호조치와 확인사항"),
            new Requirement("privacy", "개인정보 처리·흐름 자료", "SECURITY", "data.personal_info", "처리 항목·목적·근거·보유기간·파기와 정보 흐름"),
            new Requirement(
                    "integration", "외부 연계·통신 구간 자료", "INFRA", "system.external_transfer", "연계 대상·전송 데이터·인증·암호화·통신 규칙"),
            new Requirement(
                    "public", "대외 서비스 보호대책", "SECURITY", "security.public_service", "공개 범위·사용자/관리자 인증·취약점 점검 계획"),
            new Requirement("vendor", "외부 업체 보안관리 계획", "SECURITY", "security.outsourcing", "접근 권한·원격접속·자료 반출입·종료 시 회수"),
            new Requirement("cloud", "클라우드·SaaS 이용 검토자료", "SECURITY", "cloud", "서비스·저장 위치·책임 분담·인증 적용 범위·종료 계획"),
            new Requirement("ai", "AI·외부 LLM 이용 검토자료", "SECURITY", "ai", "공급자·전송 데이터·보관/학습 조건·접근통제·출력 검증"));

    /** 미확인·미입력 값을 임의로 미해당 처리하지 않는다. */
    public static Boolean condition(String value, boolean confirmed, boolean needsReview) {
        if (!confirmed || needsReview) return null;
        if ("true".equals(value) || "예".equals(value)) return true;
        if ("false".equals(value) || "아니요".equals(value)) return false;
        return null;
    }
    /** 원문 연결 없는 확인과 사유 없는 보완을 차단한다. */
    public static void validate(String status, List<Long> files, String note) {
        if (!List.of("IN_REVIEW", "NEEDS_SUPPLEMENT", "CONFIRMED").contains(status))
            throw new DomainRuleViolationException("허용되지 않는 자료 상태입니다.");
        if (files.isEmpty()) throw new DomainRuleViolationException("검토할 자료를 먼저 연결해 주세요.");
        if ("NEEDS_SUPPLEMENT".equals(status) && note.isBlank())
            throw new DomainRuleViolationException("보완이 필요한 내용을 입력해 주세요.");
    }
}
