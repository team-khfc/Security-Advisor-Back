package kr.securityadvisor.llm.service;

import java.util.List;

/**
 * 모델에 보내는 입력. 지식(공개 스니펫)과 사실(기관 비공개 레코드)을 분리해 담고, 어댑터가 블록으로 조립한다(원칙 3).
 *
 * @param system 역할·규칙 지시
 * @param knowledgeLines "[S12] 출처 — 본문" 줄들
 * @param factLines "[P7] 이름 | key=value" 줄들
 * @param history 이전 대화(질의용, 없으면 빈 목록) — role/text
 * @param user 이번 사용자 입력
 */
public record LlmPrompt(
        String system, List<String> knowledgeLines, List<String> factLines, List<LlmMessage> history, String user) {

    public static LlmPrompt of(String system, List<String> knowledgeLines, List<String> factLines, String user) {
        return new LlmPrompt(system, knowledgeLines, factLines, List.of(), user);
    }

    /** 대화 메시지(질의용). role 은 USER 또는 ASSISTANT. */
    public record LlmMessage(String role, String text) {}
}
