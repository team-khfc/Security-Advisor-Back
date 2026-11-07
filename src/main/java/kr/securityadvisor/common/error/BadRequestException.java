package kr.securityadvisor.common.error;

/** 요청 형식·값이 잘못됨(400). 업무 규칙 위반(422)과 구분한다. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
