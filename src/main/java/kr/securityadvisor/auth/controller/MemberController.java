package kr.securityadvisor.auth.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.auth.dto.ActivationCommand;
import kr.securityadvisor.auth.dto.ContactChangeCommand;
import kr.securityadvisor.auth.dto.MemberDetail;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.RoleAssignmentCommand;
import kr.securityadvisor.auth.dto.RoleChangeCommand;
import kr.securityadvisor.auth.dto.SignupRequest;
import kr.securityadvisor.auth.service.MemberAdminService;
import kr.securityadvisor.auth.service.MemberRegistrationService;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 사용자 계정 API. 회원가입은 익명 허용이며 CSRF 토큰이 필요하다. 목록·역할 변경은 기관 관리자만(불변식 32) —
 * 역할은 계정의 하위 자원(PUT …/{username}/role)으로 표현한다.
 */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberRegistrationService memberRegistrationService;
    private final MemberAdminService memberAdminService;

    /** 회원가입. 성공하면 201 과 {@code Location: /api/members/{username}}. 아이디 중복은 409, 형식 오류는 400. */
    @PostMapping
    public ResponseEntity<MemberResponse> signUp(@Valid @RequestBody SignupRequest request) {
        MemberResponse created = memberRegistrationService.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{username}")
                .buildAndExpand(created.username())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** 계정 목록(관리자). role 로 거를 수 있다. */
    @GetMapping
    public List<MemberDetail> list(
            @CurrentMember MemberPrincipal admin, @RequestParam(required = false) MemberRole role) {
        return memberAdminService.list(admin, role);
    }

    @GetMapping("/{username}")
    public MemberDetail get(@CurrentMember MemberPrincipal admin, @PathVariable String username) {
        return memberAdminService.get(admin, username);
    }

    /** 활성/비활성 설정(관리자). 비활성 계정은 로그인 불가·자동 배정 제외. 자기 자신·마지막 관리자는 422. */
    @PutMapping("/{username}/activation")
    public MemberDetail setActivation(
            @CurrentMember MemberPrincipal admin,
            @PathVariable String username,
            @Valid @RequestBody ActivationCommand command) {
        return memberAdminService.setActivation(admin, username, command);
    }

    /** 역할·활성 상태 한 번에(관리자 화면 호환). 결과는 계정 상세. */
    @PostMapping("/{username}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDetail assignRole(
            @CurrentMember MemberPrincipal admin,
            @PathVariable String username,
            @Valid @RequestBody RoleAssignmentCommand command) {
        return memberAdminService.assignRole(admin, username, command);
    }

    /** 연락처(메일 알림 주소) 변경 — 본인 또는 관리자. */
    @PutMapping("/{username}/contact")
    public MemberDetail changeContact(
            @CurrentMember MemberPrincipal actor,
            @PathVariable String username,
            @Valid @RequestBody ContactChangeCommand command) {
        return memberAdminService.changeContact(actor, username, command);
    }

    /** 역할 부여·변경(관리자). 멱등. 자기 자신·마지막 관리자 강등은 422. */
    @PutMapping("/{username}/role")
    public MemberDetail changeRole(
            @CurrentMember MemberPrincipal admin,
            @PathVariable String username,
            @Valid @RequestBody RoleChangeCommand command) {
        return memberAdminService.changeRole(admin, username, command);
    }
}
