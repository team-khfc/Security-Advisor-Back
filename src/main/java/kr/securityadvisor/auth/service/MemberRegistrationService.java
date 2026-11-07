package kr.securityadvisor.auth.service;

import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.dto.MemberResponse;
import kr.securityadvisor.auth.dto.SignupRequest;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입(F-11). 가입 계정은 운영 기관의 APPLICANT 로만 생성한다(불변식 32). */
@Service
@RequiredArgsConstructor
public class MemberRegistrationService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;

    /**
     * 계정을 만든다. 역할은 요청에 없고 도메인이 APPLICANT 로 고정한다.
     *
     * @throws ConflictException 이미 있는 아이디일 때(409)
     */
    @Transactional
    public MemberResponse register(SignupRequest request) {
        if (memberRepository.existsById(request.username())) {
            throw new ConflictException("이미 사용 중인 아이디입니다");
        }
        Member member = Member.signUp(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName(),
                request.department());
        Member saved = memberRepository.save(member);
        auditRecorder.record(
                saved.getUsername(),
                saved.getRole(),
                AuditTargetType.MEMBER,
                saved.getUsername(),
                null,
                AuditAction.MEMBER_REGISTERED,
                null,
                "회원가입 — 역할 APPLICANT 고정");
        return MemberResponse.from(saved);
    }
}
