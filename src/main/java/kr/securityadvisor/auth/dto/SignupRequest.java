package kr.securityadvisor.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청(F-11). 가입 항목은 아이디·비밀번호·표시 이름·부서(선택)로 최소화한다.
 *
 * <p>역할 필드는 일부러 두지 않는다. 요청 JSON 에 role 이 들어와도 무시되고 계정은 항상 APPLICANT 로 만들어진다(불변식 32).
 * 화면에는 "실제 개인정보를 입력하지 마세요" 안내를 둔다.
 *
 * @param username 로그인 아이디 — 영문 소문자·숫자·마침표·밑줄·하이픈, 4~50자
 * @param password 비밀번호(평문, HTTPS 전제) — 영문과 숫자를 포함해 8자 이상(bcrypt 한계로 72자 이하)
 * @param displayName 표시 이름
 * @param department 부서(선택)
 */
public record SignupRequest(
        @NotBlank
        @Size(min = 4, max = 50)
        @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*$", message = "아이디는 영문 소문자·숫자·._- 만 쓸 수 있습니다")
        String username,

        @NotBlank
        @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).*$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다")
        String password,

        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 100) String department) {}
