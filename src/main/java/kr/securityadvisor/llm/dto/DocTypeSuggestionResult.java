package kr.securityadvisor.llm.dto;

import java.util.List;

/**
 * AI 서류 유형 제안 결과. 제안일 뿐이며 선택은 사용자가 한다(불변 원칙 1). 게시본의 활성 유형이 아닌 제안은 버린다.
 *
 * @param suggestions 제안된 유형(근거 포함)
 * @param notSuggested 제안되지 않은 활성 유형(화면이 "그래도 선택 가능"으로 보여 준다)
 * @param modelReference 쓴 모델(구성/모델명)
 * @param note 표시 문구
 */
public record DocTypeSuggestionResult(
        List<Suggestion> suggestions, List<DocTypeOption> notSuggested, String modelReference, String note) {

    /** @param confidence HIGH·MEDIUM·LOW(모델 자가 평가 — 확정 사실 아님) */
    public record Suggestion(String docType, String name, String description, String reason, String confidence) {}

    public record DocTypeOption(String docType, String name, String description) {}
}
