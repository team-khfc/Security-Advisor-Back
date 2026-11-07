package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * @param username 로그인 아이디
 * @param password 비밀번호(평문, HTTPS 전제)
 */
public record LoginRequest(
        @NotBlank String username, @NotBlank String password) {}
