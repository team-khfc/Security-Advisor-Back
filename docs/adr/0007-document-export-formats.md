# ADR 0007 — 서류 출력은 형식별 변환기 플러그인 + 게시본의 서류 유형별 허용 형식

- 상태: 채택 (2026-11-08)
- 관련: FUNCTIONAL_SPEC F-06·F-07·F-08, ADR 0005(서류 유형은 데이터), docs/api/openapi.yaml

## 배경

F-06 은 서류 초안의 "미리보기·다운로드(형식·용량 표시)"를 요구하고, 기관마다 원하는 결과물(PDF, 한글 HWPX, 외부 시스템 API 전달,
메일 발송)이 다르다. 1차 범위에서는 Markdown 만 냈다. 출력 형식을 코드 분기(`if (format == PDF)`)로 늘리면 기관별 커스터마이징이
코드 수정이 되고, 서류 유형(데이터)마다 허용 형식을 다르게 둘 수 없다.

## 결정

1. **출력 형식은 열거형 `common.schema.ExportFormat`** 에 둔다(MARKDOWN, PDF, HWPX …). 값마다 확장자·미디어 타입을 가진다.
2. **변환은 `document.service.DocumentExporter` 구현 빈 하나가 형식 하나를 맡는다**(`document.export.MarkdownExporter`, `PdfExporter`).
   `DocumentExporters` 가 기동 시 형식→구현을 모으고, 한 형식에 구현이 둘이면 기동을 실패시킨다. 구현이 없는 형식은
   `GET /api/document-export-formats` 에서 `implemented=false` 로 보이고 다운로드는 503 이다 — "미구현을 구현된 것처럼 보이지 않게"(지침).
3. **서류 유형별 허용 형식은 게시본 데이터**다: `DocTypeDefinition.exportFormats`(비우면 MARKDOWN·PDF). 기관 관리자는 설정 편집·게시(F-08)로
   바꾸고, 기존 신청은 생성 시점 게시본을 따른다(불변식 8). 허용되지 않은 형식은 422.
4. 변환기는 **저장된 서류(섹션·값·출처·AI 보완)만** 입력으로 받고 업무 상태를 바꾸지 않는다. 미확정 값은 숨기지 않고 "담당자 확인 필요"로
   표시하며, 바닥글에 "합성 시연 데이터 기준, 서류 확정 ≠ 방화벽 적용"을 넣는다(불변 원칙 1·4).
5. 다운로드마다 감사 이력(DOCUMENT_DOWNLOADED: 서류·형식·파일명·크기)을 남긴다(F-14).
6. PDF 는 PDFBox 로 직접 조판하고, 한글 글꼴은 `src/main/resources/fonts/NotoSansCJKkr-Regular-subset.ttf`(Noto Sans CJK KR 서브셋,
   SIL OFL 1.1 — 같은 폴더 OFL.txt)를 부분 집합으로 임베드한다. 외부 글꼴·시스템 글꼴에 의존하지 않아 Docker 어디서나 같은 결과가 난다.

## 전달 채널(API 호출·메일)은 형식이 아니라 "배달"이다 — 1차 범위 밖

API 로 외부 시스템에 보내기·메일 발송은 "어떤 형식으로 만들었는가"와 별개의 **전달 채널**이다. 같은 구조로 `DocumentDeliverer`(채널 하나 =
구현 하나, 서류 유형별 허용 채널은 게시본 데이터, 전달마다 감사 이력·실패 재시도)를 둘 수 있으나, 외부 연동은 FUNCTIONAL_SPEC 에서
P3(메일·메신저 알림, 장비 연동)이고 내부망 분리 환경의 허용 목록 정책이 필요해 이번 범위에서 만들지 않는다. BACKLOG 에 둔다.

## 결과

- 새 출력 형식 추가 = `ExportFormat` 값 1개 + `DocumentExporter` 구현 1개. HWPX 는 값만 있고 구현이 없다(OWPML 조립 필요, 후속).
- 기관별 "이 서류는 PDF 만" 같은 정책은 코드가 아니라 게시본 편집으로 바꾼다.
- PDF 조판은 단순 3열 표(항목/값/출처·확인)다. 기관 양식(관인·서식 번호 등)을 맞추려면 양식 편집(F-08 P2)과 함께 템플릿 기반 변환기를 더한다.
