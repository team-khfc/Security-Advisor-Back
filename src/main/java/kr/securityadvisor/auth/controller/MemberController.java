package kr.securityadvisor.auth.controller;

import jakarta.validation.Valid;
import java.net.URI;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.SignupRequest;
import kr.securityadvisor.auth.service.MemberRegistrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 사용자 계정 API. 회원가입은 익명 허용이며 CSRF 토큰이 필요하다. */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberRegistrationService memberRegistrationService;

    public MemberController(MemberRegistrationService memberRegistrationService) {
        this.memberRegistrationService = memberRegistrationService;
    }

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
}
