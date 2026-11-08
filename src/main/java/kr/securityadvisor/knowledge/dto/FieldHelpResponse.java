package kr.securityadvisor.knowledge.dto;

import java.util.List;
import kr.securityadvisor.knowledge.service.SnippetHit;

/**
 * 항목 도움말(F-03 "왜 묻는지 + 공개자료 근거"). 화면이 항목 옆에 보여 준다. AI 호출 없이 설정·지식 기반에서만 만든다.
 *
 * @param fieldKey 항목 키
 * @param label 항목 이름
 * @param required 필수 여부
 * @param askRole 누가 답하는 항목인지
 * @param whyAsked 왜 묻는지(게시본 hint)
 * @param snippets 공개자료 근거(출처·위치·발췌), 관련도 순
 */
public record FieldHelpResponse(
        String fieldKey, String label, boolean required, String askRole, String whyAsked, List<SnippetHit> snippets) {}
