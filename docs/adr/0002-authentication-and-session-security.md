# ADR 0002 — 인증·세션·CSRF

- 상태: 채택 (2026-11-07)
- 관련: FUNCTIONAL_SPEC F-11, SECURE_CODING.md 1·2절, 불변식 32·36

## 결정

1. **세션 쿠키 인증**(Spring Security `HttpSessionSecurityContextRepository`). JWT 를 쓰지 않는다 — 시연 범위에서 서버 세션이 가장 단순하고 로그아웃·만료가 확실하다. 쿠키는 HttpOnly·SameSite=Lax, `SESSION_COOKIE_SECURE` 로 Secure 전환.
2. **CSRF 는 SPA 방식**: `XSRF-TOKEN` 쿠키(HttpOnly 아님) ↔ `X-XSRF-TOKEN` 헤더. 로그인·회원가입 POST 도 검사한다(로그인 CSRF 방지). 프론트는 먼저 GET 으로 쿠키를 받는다.
3. **회원가입은 APPLICANT 로만**, 요청의 역할 값은 무시(불변식 32). 담당자·관리자 계정은 seed 또는 관리자 부여.
4. **로그인 실패 제한**: 연속 N회(기본 5) 실패 시 잠금(기본 15분), 실패 기록은 업무 트랜잭션과 분리(REQUIRES_NEW). 잠김은 429.
5. **비밀번호**: bcrypt(DelegatingPasswordEncoder). 시연 계정 비밀번호는 환경변수(`DEMO_ACCOUNT_PASSWORD`)로만 받고 비어 있으면 계정을 만들지 않는다.
6. **권한 판단은 서버**: 컨트롤러는 `MemberPrincipal` 만 받고, 역할 검사·배정 검사(`ReviewerAccessPolicy`)·소유 검사(`RequestAccessPolicy`)는 서비스가 한다. 남의 자원은 404 로 숨긴다. 운영 기관은 설치당 1곳이라 기관 격리 검사는 없다(불변식 36).
7. 인증·인가 실패도 ProblemDetail(401/403)로 통일한다.

## 결과

- 테스트에서 `csrf()` 후처리기는 싱글턴 CsrfFilter 의 저장소를 바꿔치기하므로 쿠키 발급 테스트는 별도 컨텍스트(`CsrfCookieTest`)에서 한다.
