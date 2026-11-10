package kr.securityadvisor.llm.service;

import kr.securityadvisor.llm.domain.LlmConfiguration;

/** 실제 연결 시험 어댑터. 업무용 fake 모드의 성공 응답을 연결 성공으로 쓰지 않는다. */
public interface LlmConnectionProbe {
    Outcome test(LlmConfiguration configuration, String key);

    enum Outcome {
        CONNECTED("연결 시험에 성공했습니다. 모델의 응답을 확인했습니다."),
        KEY_NOT_CONFIGURED("선택한 키 참조가 서버에 설정되지 않았습니다. 설치 관리자에게 확인해 주세요."),
        AUTHENTICATION_FAILED("모델 서비스가 인증을 거부했습니다. 키의 유효성과 권한을 확인해 주세요."),
        TIMEOUT("연결 시험의 시간 제한을 초과했습니다. 서비스 상태와 연결 주소를 확인해 주세요."),
        EMPTY_RESPONSE("모델이 사용할 수 있는 답변을 반환하지 않았습니다. 모델과 응답 한도를 확인해 주세요."),
        UNAVAILABLE("모델에 연결하지 못했습니다. 모델명·서비스 상태·호출 한도를 확인해 주세요.");
        private final String message;

        Outcome(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}
