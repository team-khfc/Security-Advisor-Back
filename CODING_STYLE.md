# 보안길잡이 코딩 스타일

v0.6 · 2026-11-07 · Lombok 허용 목록 반영(1장 Java) · 이력은 문서 끝 "변경 이력" · 채택·수정은 PR로

팀 기준은 **Google 공식 스타일 가이드 준수**다(팀장 확정, 2026-11-05). Google 원문에서 필요한 규칙을 채택하고 React·Next.js·Spring Boot에 맞춰 보완한다. 아래 `[G]`는 Google 원문 기반, `[P]`는 프로젝트 선택·예외, `[F]`는 프레임워크 요구다. Google 문서의 후속 변경을 개발 중인 코드에 자동 적용하지 않는다. 보안 관련 작성 규칙은 SECURE_CODING.md가 같은 비중의 기준이다.

**적용 저장소:** Java·Spring 규칙은 Security-Advisor-Back, TypeScript·React·Next.js 규칙은 Security-Advisor-Front에 적용한다(2026-11-07 저장소 분리). 포맷터·린트 설정은 각 저장소 설정 파일이 기준이다.

## 1. 공통

- `[G]` UTF-8을 사용하고 의미를 드러내는 이름을 쓴다.
- `[P]` LF 줄바꿈·파일 끝 개행·공백 들여쓰기. `.editorconfig`·`.gitattributes`를 따른다(gradlew.bat 등 Windows 실행 파일은 CRLF 예외). 주석·설명 문서는 한국어, 식별자는 영어.
- `[P]` 이유·조건·계약을 설명하는 주석을 쓴다. 코드 동작을 그대로 읽어 주는 주석은 줄인다. 주석 규칙 상세는 AGENTS.md 7장.
- `[P]` 포맷은 사람이 맞추지 않는다. 백엔드는 Spotless, 프론트는 Prettier 결과가 정답이다. 생성물·외부 공급 파일은 손으로 포맷하지 않는다.
- `[P]` 비밀값·금지 API 등 보안 작성 규칙은 SECURE_CODING.md를 따른다.

## 2. Java — Google Java Style 기반

| 항목 | 채택 규칙 | 분류 |
|---|---|---|
| 들여쓰기 | 블록 2칸, 탭 금지 | G |
| 줄 길이 | 100자, 원문에서 인정한 예외 고려 | G |
| 선언·문장 | 최상위 타입 파일별 분리, 문장을 한 줄에 몰아 쓰지 않음 | G |
| import | wildcard import 금지, 그룹과 정렬 유지 | G |
| 이름 | 타입 UpperCamelCase, 메서드·변수 lowerCamelCase, 상수 UPPER_SNAKE_CASE | G |
| 예외 처리 | 잡은 예외를 설명 없이 무시하지 않음 | G |
| 자동 포맷 | Spotless의 포맷터 엔진을 Google 스타일 기준으로 운용 | G 기반 도구 |

세부 brace·줄바꿈·import 배치는 고정한 formatter가 결정한다. 현재 저장소 설정이 palantir-java-format이면 google-java-format 전환을 ADR로 결정하고, 전환 전까지는 "Google 스타일과 동일"이라고 표현하지 않는다.

### Spring Boot 프로젝트 규칙

- `[P]` 생성자 주입만 사용(@Autowired 필드 주입 금지). Lombok은 AGENTS.md 5장의 허용 목록만 쓰고(`@Getter`·`@RequiredArgsConstructor`·`@NoArgsConstructor(PROTECTED)`·`@Slf4j`), 금지 항목은 `lombok.config`로 컴파일 오류가 나게 한다. google-java-format은 Lombok 애너테이션을 일반 애너테이션처럼 정렬한다.
- `[P]` 요청·응답은 record DTO(XxxRequest/XxxResponse). 엔티티를 API 응답으로 직접 내보내지 않고 XxxResponse.from(entity)로 변환한다.
- `[P]` Controller는 입출력, Service는 업무 검증·트랜잭션(@Transactional 규칙은 AGENTS.md 5장), Repository는 저장소를 맡는다. 계층 의존 방향은 ArchUnit이 강제한다.
- `[P]` Map<String, Object>·JsonNode로 운영 계약을 대신하지 않는다. 오류는 GlobalExceptionHandler → ProblemDetail로 모은다.
- `[P]` 단순히 클래스 수를 늘리는 Service/ServiceImpl 쌍을 강제하지 않는다. 대체 구현·테스트 경계 등 실제 필요가 있을 때 인터페이스를 둔다.
- `[P]` 엔티티는 BaseTimeEntity 상속, @Table 명시, protected 기본 생성자, setter 대신 의미 있는 메서드.

## 3. TypeScript·React — Google TS 가이드 기반, 프로젝트 예외 명시

| 항목 | 채택 규칙 | 분류 |
|---|---|---|
| 선언 | const 우선, 재할당 필요 시 let | G |
| 문자열·문장 | 일반 문자열 single quote, 세미콜론 사용 | G |
| 내보내기 | 재사용 모듈 named export | G |
| 타입 | 객체 구조 interface 우선, union·tuple은 type | G |
| 이름 | 타입·컴포넌트 UpperCamelCase, 함수·변수 lowerCamelCase | G |
| 타입 안전 | strict 유지, 외부 입력은 unknown으로 받아 검증·좁히기, any·무분별한 단언 금지 | G 기반 적용 |
| 포맷 | 2칸, Prettier printWidth 100 | P |
| JSX 문자열 | 속성의 double quote | P |
| 파일명 | 컴포넌트 PascalCase.tsx, 훅 useXxx.ts, 그 외 camelCase.ts, 테스트 <대상>.test.tsx | P |
| Next.js 특수 파일 | page.tsx·layout.tsx·next.config 등 요구되는 default export 유지 | F, G 예외 |

- printWidth 100은 팀이 선택한 목표 폭이다. Google 공식 규칙이라고 표현하지 않으며, Prettier 출력이 Google의 모든 세부 규칙과 동일하지도 않다.
- 파일명 규칙은 v0.2의 kebab-case에서 **camelCase/PascalCase 체계로 변경**되었다(개발자 규칙 채택). 기존 kebab-case 파일의 일괄 개명은 전용 리팩터링 PR에서만 한다.
- `[P]` React state 직접 변경 금지. 비동기 호출은 로딩·실패·취소/경합 상태를 고려한다.
- `[P]` 서버 데이터는 TanStack Query 훅으로만, HTTP는 공통 httpClient(requestJson)로만. 일반 컴포넌트에서 HTTP 호출·업무 전이·출력을 한 함수에 섞지 않는다.
- `[F/P]` use client는 브라우저 기능이 필요한 경계에만. 클라이언트 번들에 서버 비밀값 금지.
- `[P]` 권한 표시와 실제 권한 검사를 구분한다. UI에서 버튼을 숨기는 것으로 서버 보호를 대체하지 않는다.
- `[P]` hooks의 조건부 호출·누락된 의존성을 임의 suppression으로 숨기지 않는다. 예외는 이유와 검증 근거를 남긴다.
- `[P]` KRDS 원본 토큰을 수정하지 않고 프로젝트 별칭과 공통 UI를 사용한다. 시각 규칙은 design.md, 화면 참고 원본은 prototype-soo의 최신 디자인 시안(디자이너 작업 영역, 수시 변경 — 구현 직전 확인, AGENTS.md 6장). 시안의 시연용 코드(브라우저 저장소, CDN 로딩)를 그대로 옮기지 않는다.

## 4. 자동화 도구

| 영역 | 도구 | 담당 범위 |
|---|---|---|
| Java | Spotless (Gradle, Google 스타일 기준 엔진) | 코드 배치·포맷 검사 |
| Java 구조 | ArchUnit (테스트로 포함) | 계층 의존·패키지 규칙 |
| TS/TSX | Prettier + ESLint(Next.js·TypeScript·React hooks 규칙, no-eval 등 SECURE_CODING 금지 API 포함) | 포맷·정적 검사 |
| 공통 | EditorConfig | 인코딩·개행·들여쓰기 |

도구·플러그인 버전은 저장소 설정 파일이 기준이며 latest를 쓰지 않는다. Java 파일을 두 포맷터로 처리하지 않고, ESLint와 Prettier가 상충하게 구성하지 않는다. CI에서는 수정 명령이 아니라 검사 명령을 실행한다.

## 5. 예외 관리

| 예외 | 이유 | 적용 범위 |
|---|---|---|
| Next.js default export | 프레임워크 파일 계약 | 요구되는 페이지·레이아웃·특수 파일·설정 |
| printWidth 100 | 팀 가독성·기존 코드 균형 | 직접 관리하는 TS/TSX |
| gradlew.bat CRLF | Windows 실행 파일 요구 | 해당 파일 |
| vendor·생성물 제외 | 공급·생성 원본 보존 | public/vendor, .next, out, build, .gradle 등 |
| 한 번의 대량 포맷 정리 | 리뷰·충돌 범위 통제 | 전용 포맷 PR에서만 |

새 예외는 사유·범위·검증 방법을 함께 추가한다. 특정 개발자의 IDE 스타일을 임의로 적용하지 않는다.

## 참고 자료

- Google Java Style Guide: https://google.github.io/styleguide/javaguide.html
- Google TS Style Guide: https://google.github.io/styleguide/tsguide.html
- google-java-format: https://github.com/google/google-java-format
- Spotless: https://github.com/diffplug/spotless
- Prettier 옵션: https://prettier.io/docs/options
- KISA JavaScript 시큐어코딩 가이드(2023): 팀 Claude 프로젝트에 PDF 보관, 실행 규칙은 SECURE_CODING.md

## 변경 이력

최신순. 머리말에는 현재 버전만 둔다.

| 버전 | 날짜 | 변경 |
|---|---|---|
| v0.6 | 2026-11-07 | Lombok 허용 목록 반영(1장 Java) |
| v0.5 | 2026-11-07 | 적용 저장소를 Security-Advisor-Back(Java)·Front(TS)로 변경 |
| v0.4 | — | 화면 참고 원본을 prototype-soo 최신 시안으로 변경 |
| v0.3 | — | Security-Advisor 저장소 기준(Next.js + Spring Boot/Gradle Kotlin DSL/PostgreSQL) |
