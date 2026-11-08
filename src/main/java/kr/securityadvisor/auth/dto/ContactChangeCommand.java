package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 연락처(메일 알림 주소) 변경. 본인 또는 기관 관리자. 비우면 메일 알림 해제.
 *
 * @param email 수신 주소(선택). 시연에서는 실존 주소를 넣지 않는다
 */
public record ContactChangeCommand(@Email @Size(max = 200) String email) {}
