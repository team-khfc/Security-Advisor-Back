package kr.securityadvisor.llm.service;

/**
 * 모델 응답. 원문(text)은 호출한 서비스가 파싱·검증하며, 감사 이력에는 modelReference·configurationId 만 남긴다(불변식 25).
 *
 * @param modelReference 구성 이름/모델명(키 없음)
 * @param configurationId llm_configurations ID(fake 구현은 null)
 */
public record LlmResult(String text, String modelReference, Long configurationId) {

    public LlmResult(String text, String modelReference) {
        this(text, modelReference, null);
    }
}
