# 규칙 문서 원본과 공개본 출처

동기화 작업일: 2026-11-10 · 원본 개정일: 2026-11-07 (`design.md`는 2026-11-06)

## 공개본 변환 범위

이 공통 공개본은 사용자가 이번 작업에 제공한 최신 프로젝트 문서 8종과 v1.14 요약 지침을 기준으로 준비했다. **팀 실명은 원본에 정의된 역할명으로, 개인 이메일은 `비공개`로 치환했다.** 치환 뒤 중복되는 역할 표현과 조사는 필요한 범위에서만 정리했다. 업무 규칙, 권한, 일정, 절차, 명령, 머리말의 버전·개정일은 원본 그대로 유지했다. 개인정보가 포함된 원문과 치환 대응표는 공개 저장소에 추가하지 않는다.

공개본 변환은 새로운 업무 규칙 버전을 정하는 작업이 아니다. 현재 기준은 [PROJECT_CONTEXT.md 5장](../PROJECT_CONTEXT.md#5-기준-버전-표-공식), 참조 순서는 [claude/INSTRUCTIONS.md](../claude/INSTRUCTIONS.md), 문서 개정 절차는 [PROJECT_RULES.md 7-1](../PROJECT_RULES.md#7-1-문서-버전-관리와-개정-절차)을 따른다. 사본을 갱신할 때는 같은 공개본과 이 변환 기록을 함께 사용한다.

`PROJECT_RULES.md`의 대회 운영기관 공용 문의 메일·대표 문의 전화는 공개 기관 연락처로 유지했다. 운영 기관 예시, 팀 역할·부서, 참고 자료·공식 출처 URL도 유지했다. `design.md`는 원본과 바이트 단위로 같다.

## 첨부 원본별 추적 정보

제공 원경로는 이번 작업에 제공된 첨부파일의 작업공간 상대 경로다. 원본은 읽기 전용으로 두고 저장소에는 공개본 한 경로만 둔다. 아래 해시는 UTF-8 파일 전체 바이트 기준이다. 작업일과 원본 개정일을 구분하며, 11월 10일 동기화를 이유로 원본의 11월 7일·11월 6일 개정일을 변경하지 않았다.

| 문서 | 기준 버전 | 원본 개정일 | 제공 원경로 | 원본 SHA-256 | 공개본 SHA-256 | 비식별 변환 |
|---|---|---|---|---|---|---|
| PROJECT_CONTEXT.md | v3.5 | 2026-11-07 | project_sources/07-PROJECT_CONTEXT.md | a13b836cce3589685ea0d1997c2d8a23b3d2acb8e05708dd9e84b2c17b53a4d6 | c4641a23d8bdd58a29ec0bcf7b32c03710228a60cce7ba6157c75c9e75696948 | 실명 5곳, 개인 이메일 0곳, 역할·조사 정리 3곳 |
| FUNCTIONAL_SPEC.md | v1.12 | 2026-11-07 | project_sources/06-FUNCTIONAL_SPEC.md | 12a0706e915502331a849245cfdab92cc5311d782274078cb5d6f056c7ddacca | 12a0706e915502331a849245cfdab92cc5311d782274078cb5d6f056c7ddacca | 실명 0곳, 개인 이메일 0곳, 역할·조사 정리 0곳 |
| AGENTS.md | v1.17 | 2026-11-07 | project_sources/02-AGENTS.md | 8ccb9919b00c9d615aa278aa9d63aa18eeacf178af0f461a94522ab80ace1b19 | a50a5ca711f9f3692d29a79ed6e321b2ebfba6671d25a8c06f433e91b3685741 | 실명 2곳, 개인 이메일 0곳, 역할·조사 정리 2곳 |
| PROJECT_RULES.md | v1.15 | 2026-11-07 | project_sources/08-PROJECT_RULES.md | bb5df13e3f7a17bb476c574c6d86b30b4d5f28756a94e5b82b556c64be27b141 | 7b8f40f693689ad9b9806969fcb4d87f279d24b1037da57e329fe9a6dda2bffc | 실명 28곳, 개인 이메일 5곳, 역할·조사 정리 10곳 |
| CODING_STYLE.md | v0.6 | 2026-11-07 | project_sources/03-CODING_STYLE.md | 4b3114acb719da4871c14407197a0497d6bc50e487a8f6499f1a09fb9ab52464 | 4b3114acb719da4871c14407197a0497d6bc50e487a8f6499f1a09fb9ab52464 | 실명 0곳, 개인 이메일 0곳, 역할·조사 정리 0곳 |
| SECURE_CODING.md | v1.5 | 2026-11-07 | project_sources/09-SECURE_CODING.md | 5c9211724412bdcd471279d76cc5c41cbb157b36af4d4de93daa31a4972cd774 | 5c9211724412bdcd471279d76cc5c41cbb157b36af4d4de93daa31a4972cd774 | 실명 0곳, 개인 이메일 0곳, 역할·조사 정리 0곳 |
| CONTRIBUTING.md | v0.17 | 2026-11-07 | project_sources/04-CONTRIBUTING.md | 0de2942ba6045aeac613c22405f8419b433b67a90326876abbb3c50ca115a9ca | e296bf389fdd2ec47c8550d3691e97ef153605510f2b0dcd1421c2a12faf05f2 | 실명 9곳, 개인 이메일 0곳, 역할·조사 정리 1곳 |
| design.md | v3.1 | 2026-11-06 | project_sources/05-design.md | 5dbc2fbb05decc7e9a98523b3f103e2b21d78d6191bbe6826fcce678afc802c1 | 5dbc2fbb05decc7e9a98523b3f103e2b21d78d6191bbe6826fcce678afc802c1 | 실명 0곳, 개인 이메일 0곳, 역할·조사 정리 0곳 |

`claude/INSTRUCTIONS.md`는 사용자가 대화에 직접 제공한 **v1.14 · 2026-11-07** 요약 지침의 제목부터 작업 태도 끝까지 옮겼다. 첨부 파일 8종의 해시 표와 구분하며, 전달된 요약 지침의 개인정보 치환이나 업무 내용 수정은 없었다. 공개본 SHA-256은 `1327b6ce23b625a6df2704c041a11bd0719d4a0000ea049be0af625b997e1715`이다.

## 저장소별 반영 추적

공통 공개본의 준비와 각 저장소 기본 브랜치의 반영은 구분한다. 아래 저장소의 문서 동기화 PR에서 대상 기준 커밋, 검사 결과, 실제 병합 여부를 확인한다. 이 문서가 한 저장소에 존재한다는 사실만으로 다른 저장소의 반영까지 완료됐다고 보지 않는다.

| 대상 저장소 | 반영 근거 |
|---|---|
| Security-Advisor-Back | [문서 동기화 PR과 병합 커밋](https://github.com/team-khfc/Security-Advisor-Back/pulls)에서 추적 |
| Security-Advisor-Front | [문서 동기화 PR과 병합 커밋](https://github.com/team-khfc/Security-Advisor-Front/pulls)에서 추적 |
| Security-Advisor | [문서 동기화 PR과 병합 커밋](https://github.com/team-khfc/Security-Advisor/pulls)에서 추적 |

프로젝트 설정의 지침 칸, 외부 공유 문서, 과거 배포본은 저장소 PR과 별개의 반영 대상이다. 이 파일만으로 해당 위치의 갱신 완료를 보고하지 않는다. 과거 PR·ADR·검증 기록의 당시 버전은 소급하여 바꾸지 않는다.

## 확인 방법과 한계

`python3 tools/check_doc_versions.py`는 PROJECT_CONTEXT 5장과 9종 문서 머리말의 버전·개정일, 정해진 단일 경로, 첨부 8종 공개본의 해시, 문서에 남은 이메일·전화 형식을 검사한다. 이 도구는 Front에서 사용하던 스크립트를 변경 없이 공통 공개본에 포함했다. 요약 지침도 버전·개정일·단일 경로 검사 대상이며, 첨부 8종의 공개본 해시 검사와 구분된다.

검사는 저장소 내부의 정합성을 확인하며 외부 프로젝트 원본이 새로 개정됐는지 자동으로 조회하지 않는다. 새 원본을 받으면 개정 절차에 따라 문서와 이 출처 기록을 함께 갱신해야 한다. 일반적인 개인정보 의미 판별이나 모든 종류의 비밀값 탐지를 보장하는 도구는 아니다. 원본과 공개본의 차이는 위 비식별 치환·역할 표현·조사 정리 범위인지 별도로 확인한다.

`design.md`에 남아 있는 예전 시안 경로·커밋과 미결 시각 항목은 원본 기록이다. 이번 동기화에서는 임의로 고치지 않았다. 디자인 원본은 `prototype-soo/docs/design.md`이며 개별 화면 작업은 AGENTS.md 6장에 따라 그때의 최신 시안·MERGE_NOTES를 확인하고 작업 기록에 참조 커밋을 남긴다.
