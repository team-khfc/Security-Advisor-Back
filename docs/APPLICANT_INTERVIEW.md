# 신청자 항목별 AI 인터뷰

사업 건에 고정된 게시본의 공통 질문을 신청자 본인이 이해하고 작성하는 기능이다. 담당자 전용 질의(`/api/security-requests/{id}/inquiries`)와 분리한다.

- `GET /api/business-cases/{caseId}/interview-turns`: 소유 신청자의 최근 30개 대화를 오래된 순서로 반환한다.
- `POST /api/business-cases/{caseId}/interview-turns`: `{fieldKey, mode: ASK|SUGGEST, text, draft?, expectedRevision}`. 사업 건 revision을 사용한다. 설명·후보만 저장하며 답변은 바꾸지 않는다.
- `POST /api/business-cases/{caseId}/interview-turns/{turnId}/applications`: `{expectedRevision}`. 서버에 저장된 후보를 `AI_INFERRED`, `confirmed=false`로 적용한다. 이후 기존 답변 confirmation API로 사람이 확인한다.

고정 게시본 질문, 신청자가 작성한 공통 답변, 업로드 문서의 미확정 요약, 연결된 공개 지식만 AI에 전달한다. 기관 비공개 컨텍스트와 담당자 항목의 값은 전달하지 않는다. 사용한 근거 태그·설명을 반환하고 존재하지 않는 태그, 추측 IP, 입력 형식에 맞지 않는 후보는 적용할 수 없게 한다. 실제 사실의 정확성은 사용자가 확인해야 한다.

다른 신청자는 404, 담당자·관리자는 403이다. 작성 가능한 신청이 없거나 담당자 항목을 추천하려 하면 422다. 생성 뒤 사업 건·공통 답변·문서 요약이 변경되면 적용 시 409로 거부한다. 이미 확인한 답변은 기존 Answer 도메인 규칙으로 보호하며 반복 적용은 추가 쓰기 없이 반환한다.

`V202611100100__create_interview_turns.sql`은 새 테이블·인덱스만 추가한다. 기존 신청과 답변을 수정하지 않는다. 복구 시 새 API 사용을 중단하고 기존 답변 API를 사용할 수 있다. 데이터 손실을 피하려면 테이블을 삭제하지 않는다.

`InterviewApiTest`는 미리보기/적용/확인, 소유권, 담당자·서류별 필드 제외, 오래된 제안, 근거 없는 출력, 잘못된 AI 응답을 PostgreSQL 환경에서 검증한다. 실제 AI 품질은 이 결정적 테스트와 별도로 도커 live 모드에서 확인한다.

연계 이슈: [Back #1](https://github.com/team-khfc/Security-Advisor-Back/issues/1), [Front #24](https://github.com/team-khfc/Security-Advisor-Front/issues/24).
