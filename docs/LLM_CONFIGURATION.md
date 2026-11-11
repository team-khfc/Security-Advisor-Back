# LLM 구성 변경과 점검

설정 소유자는 Back이며 Front는 관리자 화면을 제공한다. DB 직접 수정 대신 **기관관리자 → AI 모델 구성(`/models`)**을 사용해 변경 비교·이력·충돌 검사를 유지한다.

## 1. 실행 환경 설정

Back 단독 또는 통합 배포 저장소의 `.env`에 다음을 설정한다. 실제 키는 파일·로그·화면 캡처·Git에 포함하지 않는다.

```dotenv
LLM_MODE=live
OPENAI_API_KEY=
OPENROUTER_API_KEY=
LLM_ALLOWED_HOSTS=api.openai.com,openrouter.ai,ollama,localhost,host.docker.internal
LLM_ALLOWED_KEY_ENVS=OPENAI_API_KEY,OPENROUTER_API_KEY,OLLAMA_API_KEY
```

빈 키에는 실제 사용할 공급자의 키만 넣는다. `fake`는 고정 모의 응답이며 실제 모델 검증이 아니다. 키·허용 목록 변경 후 해당 compose 루트에서 서버를 재생성한다.

```bash
docker compose up -d --no-deps --force-recreate app
```

단순 `restart`는 변경한 컨테이너 환경을 반영하지 않는다. 새 키 이름을 쓴다면 허용 목록뿐 아니라 앱 프로세스/컨테이너에도 그 변수가 전달돼야 한다. Front의 `.env.local`이나 `NEXT_PUBLIC_*`에 키를 넣지 않는다.

## 2. 관리자 화면에서 추가·수정

| 항목 | 설정 기준 |
|---|---|
| 이름·공급자·모델 | 공급자에서 실제 사용할 수 있는 모델 식별자와 지원되는 공급자 선택 |
| base URL | OpenAI 호환 API 주소. 서버의 허용 호스트에 포함되어야 하며 스킴·주소 검증도 통과해야 함 |
| 키 참조(`apiKeyEnv`) | 키 자체가 아닌 환경변수 이름(예: `OPENAI_API_KEY`) |
| 용도 | `INTERVIEW`, `SUMMARY`, `DOCUMENT`, `INQUIRY`. 협의 요약·최종 초안은 `DOCUMENT` |
| 출력 한도·온도·대기 시간 | 화면/API 검증 범위 내에서 모델 특성과 출력 크기에 맞게 설정 |
| 우선순위·활성 | 용도에 맞는 활성 구성 중 키·호스트 조건을 만족하는 작은 우선순위 값부터 선택. 같은 값이면 ID 순 |
| 변경 사유·revision | 변경 사유를 남기고 최신 revision으로 저장. 충돌 시 새 값을 읽고 다시 비교 |

1. 기존 값과 변경안을 비교한다.
2. 연결 시험 결과를 확인하고 변경 사유와 함께 저장한다. 연결 시험은 비용이 발생할 수 있다.
3. 실제 대상 용도에서 한 번 호출해 모델 선택·출력 한도·근거 연결을 확인한다. **연결 시험 성공만으로 기본 모델이 바뀌지는 않는다.**
4. 이력에서 과거 구성을 골라 비교·복원할 수 있다. 복원도 새로운 변경으로 기록되며 최신 revision·허용 목록 검증을 통과해야 한다.

기존 신청·제출 문서를 다시 쓰지 않고 다음 AI 호출부터 변경된 선택 기준을 적용한다. 모델 호출 실패 시 다른 공급자로 자동 재시도된다고 가정하지 않는다. 실제 선택 구현과 오류 안내를 확인한다.

## 3. API와 문제 해결

| 기능 | API |
|---|---|
| 목록·추가·수정 | `GET/POST /api/llm-configurations`, `PUT /api/llm-configurations/{id}` |
| 관리자 정보·변경 비교 | `GET /api/llm-configurations/management`, `POST /api/llm-configurations/{id}/comparisons` |
| 이력·복원 | `GET /api/llm-configurations/{id}/history`, `POST /api/llm-configurations/{id}/restorations` |
| 연결 시험 | `POST /api/llm-connection-tests`, `GET /api/llm-connection-tests/{id}` |

정확한 요청 DTO는 Back의 `docs/api/openapi.yaml` 또는 실행 중 Swagger를 따른다. 관리자 세션과 CSRF 보호를 유지하며 무인증 curl 예제를 그대로 실행하지 않는다.

| 증상 | 확인 |
|---|---|
| 고정 응답 | 앱의 `LLM_MODE`, 환경 반영 여부 |
| 사용할 구성 없음 | 활성·용도·키 참조 허용·키 존재·호스트 허용 |
| 다른 모델 호출 | 우선순위, 동일 우선순위 ID, 용도별 활성 구성 |
| 호출 시간 초과 | 모델 `timeoutSeconds`, 프론트 중계 대기 시간, 외부 프록시 제한을 함께 확인. 로그인 세션 만료와 구분 |
| 출력 중단·근거 오류 | 출력 한도·응답 형식·제공된 근거 식별자 확인. 실패 결과를 성공으로 저장하지 않음 |
| 409 충돌 | 최신 구성을 다시 읽고 변경 비교 후 재저장 |

검증된 특정 모델·토큰 값은 통합 배포 README와 당시 검증 기록을 참고한다. 공급자 모델 지원·권한은 실행 시 확인하며 문서에 적힌 과거 검증을 현재 공급자의 지원 보장으로 해석하지 않는다.
