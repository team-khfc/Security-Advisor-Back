package kr.securityadvisor.llm.adapter;

import kr.securityadvisor.llm.service.LlmPrompt;

/** 지식·사실 블록을 시스템 메시지로 조립한다. 구현체(Spring AI·Fake)가 공유한다. */
final class PromptAssembler {

    private PromptAssembler() {}

    static String systemText(LlmPrompt prompt) {
        StringBuilder sb = new StringBuilder(prompt.system()).append("\n\n");
        sb.append("[기관 사실] 아래 블록에 있는 서버명·IP·대역·자산만 사실로 인용할 수 있다. 블록에 없는 내부 정보는 존재하지 않는 것으로 보고 절대 추측하거나 만들어내지 마라.\n");
        if (prompt.factLines() == null || prompt.factLines().isEmpty()) {
            sb.append("(제공된 기관 사실 없음)\n");
        } else {
            prompt.factLines().forEach(line -> sb.append(line).append('\n'));
        }
        sb.append("\n[공개 지식] 제안·답변의 근거는 아래 조각에서만 인용한다. 인용은 조각 ID(S숫자)로 한다.\n");
        if (prompt.knowledgeLines() == null || prompt.knowledgeLines().isEmpty()) {
            sb.append("(제공된 지식 없음)\n");
        } else {
            prompt.knowledgeLines().forEach(line -> sb.append(line).append('\n'));
        }
        return sb.toString();
    }
}
