package kr.securityadvisor.common.schema;

import java.util.Set;
import kr.securityadvisor.common.security.MemberRole;

/**
 * 게시본이 정의하는 서류 유형 하나(ADR 0005 — 서류 유형은 데이터). 운영자가 새 서류를 추가하면 여기에 한 줄이 늘어난다.
 *
 * <p>코드 분기(예: "방화벽이면 규칙 생성")는 유형 코드가 아니라 아래 행위 속성으로 한다. 새로운 종류의 행위가 필요한
 * 서류만 코드 작업 대상이다.
 *
 * @param code 유형 코드(불변 식별자)
 * @param name 표시 이름(예: 방화벽 정책 허용 신청서)
 * @param description 선택 설명
 * @param active false 면 새 사업 건에서 고를 수 없다(기존 신청은 게시본 고정으로 유지)
 * @param templateKey 서류 초안 양식 키(F-06)
 * @param generatesFirewallRules 4단계 확정 시 제출본 규칙 행마다 FirewallRule 을 만드는지(F-15)
 * @param reviewFields 2단계에서 협의가 필요한 분야(INFRA·SECURITY). 1차 범위는 INFRA 선행 고정
 * @param sortOrder 화면 정렬 순서
 * @param exportFormats 이 서류를 내려받을 수 있는 출력 형식(F-06). 비우면 MARKDOWN·PDF. 기관 관리자가 설정 편집(F-08)으로 바꾼다
 */
public record DocTypeDefinition(
        DocTypeCode code,
        String name,
        String description,
        boolean active,
        String templateKey,
        boolean generatesFirewallRules,
        Set<MemberRole> reviewFields,
        int sortOrder,
        Set<ExportFormat> exportFormats) {

    public static final Set<ExportFormat> DEFAULT_EXPORT_FORMATS = Set.of(ExportFormat.MARKDOWN, ExportFormat.PDF);

    public DocTypeDefinition {
        exportFormats =
                exportFormats == null || exportFormats.isEmpty() ? DEFAULT_EXPORT_FORMATS : Set.copyOf(exportFormats);
    }

    /** 출력 형식을 기본값으로 두는 생성자(테스트·기존 호출부). */
    public DocTypeDefinition(
            DocTypeCode code,
            String name,
            String description,
            boolean active,
            String templateKey,
            boolean generatesFirewallRules,
            Set<MemberRole> reviewFields,
            int sortOrder) {
        this(code, name, description, active, templateKey, generatesFirewallRules, reviewFields, sortOrder, null);
    }

    public boolean allowsExport(ExportFormat format) {
        return exportFormats.contains(format);
    }
}
