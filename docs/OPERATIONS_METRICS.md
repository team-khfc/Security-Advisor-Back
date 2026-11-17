# 운영 상태·지표와 Actuator 접근 통제

2026-11-17 · GitHub 개발본과 검증용 Docker 기준

## 접근 범위

- `GET /actuator/health`: 비로그인 상태에서도 UP/DOWN 상태와 활성 상태 그룹 이름(liveness/readiness)만 제공한다. DB 주소·컴포넌트·상세 원인은 공개하지 않는다. 그룹별 상태 조회에도 같은 상세 비공개 정책을 적용한다.
- `/actuator/info`, `/actuator/metrics`, `/actuator/metrics/{name}`: 유효한 관리자 세션만 조회할 수 있다. 일반 신청자·ICT인프라·정보보안 담당자는 403, 비로그인은 401이다. 기존 계정 비활성화·역할 변경 세션 검증도 그대로 적용한다.
- env/configprops/heapdump/logfile/loggers/shutdown은 비활성화한다. 그 외 Actuator 엔드포인트와 발견용 링크는 관리자에게도 차단한다. JMX 노출도 끈다. 별도 SecurityFilterChain으로 업무 API의 세션·CSRF 검사를 우회하지 않는다.
- 관리자 허용 여부는 EndpointRequest로 판정하므로 기본 경로 변경 시에도 같은 접근 정책을 사용한다. 건강 상태 외 운영 엔드포인트는 브라우저 프론트가 아닌 백엔드 관리 경로에서 확인한다.

## 지표

| 이름 | 분류와 의미 |
|---|---|
| `http.server.requests` | Spring MVC 기본 관측: 경로 **유형**, 메서드, HTTP 상태별 요청 수·누적/최대 소요 시간. 오류율은 4xx/5xx 건수와 전체 건수로 계산 |
| `jvm.memory.used`, `jvm.threads.live`, `hikaricp.connections.active` 등 | 기존 Actuator/Micrometer가 제공하는 JVM·DB 연결 풀 지표 |
| `securityadvisor.llm.executions` | 모든 업무 AI 진입점의 실제 백엔드 호출 시간/횟수. category=INTERVIEW/SUMMARY/DOCUMENT/INQUIRY, outcome=success/failure/timeout |
| `securityadvisor.reminder.executions` | 기한·방화벽 재검토 알림 실행 시간/횟수. category=scheduled/manual, outcome=success/failure/timeout |
| `securityadvisor.reminder.last.success` | scheduled/manual별 마지막 정상 실행의 Unix 시각(초). 알림 기준일 `asOf`와 구분 |
| `securityadvisor.notification.executions` | 화면 알림 저장 및 외부 채널 호출 시간/횟수. category=in_app/EMAIL/WEBHOOK, outcome=success/failure/timeout/skipped |

지표는 해당 동작이 처음 실행된 뒤 나타난다. Timer의 COUNT는 실행 횟수, TOTAL_TIME은 누적 초, MAX는 최근 최대 시간이다. 화면 알림의 skipped는 중복·수신 권한 등의 사유로 새 알림이 만들어지지 않은 실행이다. 외부 채널 success는 호출이 예외 없이 끝났다는 뜻이며 메일·메신저 실제 수신 확인이 아니다. 꺼진 채널은 실행하지 않는다.

LLM 지표는 모델 호출 결과를 의미하며 이후 답변 구조/근거 검증·업무 저장 성공을 보증하지 않는다. 연결 시험은 별도 시험 기능이고 이 업무 호출 집계에 포함하지 않는다. 감싼 예외의 원인 체인에서 HTTP/socket/일반 timeout을 분류하며 원문 오류 메시지를 태그에 넣지 않는다.

## 조회 예

검증용 백엔드 `http://localhost:8082`에서 기존 세션 로그인 API로 관리자로 로그인한 후 같은 쿠키로 다음 경로를 조회한다. 프론트 3002의 업무 프록시에 Actuator를 추가하지 않았다.

```text
GET /actuator/metrics
GET /actuator/metrics/http.server.requests
GET /actuator/metrics/securityadvisor.llm.executions?tag=category:INTERVIEW&tag=outcome:success
GET /actuator/metrics/securityadvisor.reminder.last.success?tag=category:scheduled
GET /actuator/metrics/securityadvisor.notification.executions?tag=category:in_app
```

최근 기간의 오류율은 수집 간격별 COUNT 증가분으로 계산한다. 마지막 성공 시각이 예약 주기를 초과해 오래되면 예약 실패/중단 여부를 확인하되, 현재 스케줄이 꺼져 있는지도 함께 확인한다. 이 지표 조회 기능이 장애 알림을 외부에 자동 발송하지는 않는다.

## 운영 및 보존

- API 키·프롬프트·첨부 원문·사용자 답변·회원/신청 ID·모델 주소를 사용자 정의 태그에 넣지 않는다. 태그 값은 한정된 목록만 사용한다. 기본 HTTP 관측도 경로 변수 값을 경로 유형으로 묶는다.
- 지표 기록 실패는 원래 업무 결과를 바꾸지 않는다. 알림 실패를 업무 실패로 전파하지 않는 기존 규칙도 유지한다.
- 현재 지표는 프로세스 메모리 기반으로 재기동 시 초기화된다. 감사 이력과 업무 데이터 보존을 대체하지 않는다. 장기 추세·경보를 위한 Prometheus/Grafana 수집기는 이번 변경에 포함하지 않는다.
- 검증 Compose의 백엔드는 호스트 `127.0.0.1:8082`에만 공개되어 있다. 실제 운영에서는 신뢰할 수 있는 관리망/프록시 경로에서만 운영 API에 접근하도록 제한하고 HTTPS를 적용한다. 별도 관리 포트 도입 시에도 네트워크 제한과 인증 검증을 함께 유지한다.
- 의존성·DB 스키마·업무 API 계약·프론트 코드는 바꾸지 않는다. 공공 GitLab과 기존 제출 환경에는 반영하지 않는다.

## 검사

`./gradlew spotlessApply check`에 최소 health·역할별 metrics/info 접근·민감 엔드포인트 차단, LLM 성공/감싼 timeout, 외부 알림 실패 격리, 예약 실행 성공 시각·실패·중복 제외 지표 검사를 포함한다. 실제 검증용 API에서는 합성 신청으로 실제 AI 인터뷰와 기한 알림을 실행하고 지표 증가 및 경로 변수/쿼리 값의 비노출을 확인한다. 브라우저 검증은 보류한다.

2026-11-17 검증 결과: 자동 검사 258건 중 256건 통과·기존 제외 2건·실패 0건. 검증용 Docker의 합성 사업 23/신청 24에서 실제 AI 인터뷰, 날짜를 지정한 기한 알림 2회, 중복 제외, API 상태별 지표를 확인했다. 익명 401·신청자/분야 담당자 403·관리자 200 및 관리자도 민감 경로 403을 확인했다. 별도 합성 관리자 계정의 역할 변경과 비활성화 후 기존 세션은 401로 차단됐으며 검증 계정은 비활성 상태로 종료했다.
