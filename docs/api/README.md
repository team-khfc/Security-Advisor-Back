# API 계약 (OpenAPI)

`openapi.yaml` 은 Back 이 소유하는 API 계약이다(AGENTS.md 0장·5장). Front 는 이 파일로 모의 API 를 만든다.

- 생성: `.\gradlew.bat test --tests "*OpenApiExportTest*" -PexportOpenApi=true` (Docker 필요) → `docs/api/openapi.yaml` 갱신
- 계약을 바꾸는 PR 은 이 파일을 함께 갱신한다. 손으로 고치지 않는다(컨트롤러·DTO 가 원본).
- 실행 중인 서버에서는 `/v3/api-docs.yaml`, 화면은 `/swagger-ui.html`.
