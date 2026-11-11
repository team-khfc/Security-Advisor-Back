# Back 개발·유지보수 가이드

먼저 [적용 규칙](RULES_GUIDE.md)을 확인한다. 이 저장소는 업무 규칙·권한·DB·AI·문서 생성과 API 계약을 관리한다. 화면은 Front, 전체 실행 배포본은 SecurityAdvisor에서 관리한다.

## 설치와 실행

Docker Desktop(Linux 컨테이너) 또는 Docker Engine + Compose v2를 준비한다. 최초 실행에만 `.env.example`을 `.env`로 복사한다. PowerShell은 `Copy-Item .env.example .env`를 사용한다.

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps -a
docker compose logs --tail 50 app
```

키 없이 확인하려면 실행 전에 `.env`의 `LLM_MODE=fake`를 지정한다. 기본 예시는 live일 수 있다. API는 `http://localhost:8080/swagger-ui.html`, 상태는 `/actuator/health`다. 호스트 API 포트는 `APP_PORT`로 변경한다. 기본 `applicant`, `applicant2`, `infra`, `security`, `admin` 계정은 최초 시연 비밀번호 설정에 따라 생성되며 통합 배포의 지정 계정과 구분한다.

## 로컬 Java 개발

JDK 25·JAVA_HOME·Python 3와 접근 가능한 PostgreSQL 17이 필요하다. 테스트의 Testcontainers는 Docker를 사용한다. `.env`는 Spring의 `spring.config.import`로 읽으며 프로세스 환경변수가 우선한다.

기본 compose의 DB는 호스트 포트를 공개하지 않는다. 기존 PostgreSQL을 사용하거나 다음 **개발용 추가 파일**을 만들고 로컬 접속만 공개한다.

```yaml
# compose.local-db.yaml (개발 PC 전용)
services:
  db:
    ports:
      - "127.0.0.1:55432:5432"
```

```bash
docker compose -f compose.yaml -f compose.local-db.yaml up -d db
./gradlew bootRun
```

실행 전 `.env`의 `DB_URL=jdbc:postgresql://localhost:55432/boan`과 `DB_USERNAME`·`DB_PASSWORD`를 초기 DB의 `POSTGRES_*` 값에 맞춘다. Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다. 이미 app 컨테이너가 8080을 사용한다면 먼저 `docker compose stop app`을 실행하거나 로컬 `SERVER_PORT`를 다르게 지정한다.

## DB 구성과 스키마

| 설정 | 적용 범위 |
|---|---|
| `POSTGRES_DB/USER/PASSWORD` | compose DB 최초 생성 및 app 컨테이너 접속(기본 DB/사용자 boan) |
| `DB_URL/USERNAME/PASSWORD` | 로컬 Java 실행. compose app의 명시적 environment가 `.env`의 DB 값을 덮어씀 |
| `pgdata` 볼륨 | PostgreSQL 데이터 |
| `storage` 볼륨 | 컨테이너 `/data/storage`의 첨부·생성 문서 |
| `STORAGE_DIR` | 로컬 파일 저장 위치. compose는 `/data/storage`로 고정 |

`.env`의 POSTGRES 값을 바꿔도 기존 볼륨의 사용자·비밀번호·DB 이름은 바뀌지 않는다. 기존 설치의 변경은 백업 후 DB 관리자 변경과 앱 접속값을 함께 맞춘다. 외부 DB를 쓰려면 compose의 app `DB_*` override도 변경해야 한다. `.env`만 바꿔서는 기본 `db` 서비스 연결이 유지된다.

스키마 원본은 `src/main/resources/db/migration/`이며 JPA는 `ddl-auto: validate`다. 기존 배포 마이그레이션은 수정하지 않고 새 `V<연월일시분>__설명.sql`을 추가한다. 핵심 데이터는 사업·신청·답변, 협의·배정, 제출본·문서, 설정 게시본·지식, 모델 구성·변경 이력, 감사·알림·방화벽 규칙으로 나뉜다. 정확한 컬럼·제약은 마이그레이션과 엔티티가 기준이다. 기관은 설치 설정이며 기관 테이블을 추가하지 않는다.

DB 확인(컨테이너 환경값을 사용하므로 호스트 쉘에서 비밀값을 출력하지 않음):

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

## 백업·복원

애플리케이션 쓰기를 중지한 일관된 시점에 DB와 storage를 함께 백업하고 배포 커밋을 기록한다. 아래는 DB 덤프 생성·복사 예시다(파일명은 매번 고유하게 지정).

```bash
docker compose exec db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/backup.dump'
docker compose cp db:/tmp/backup.dump ./backup.dump
```

복원은 원본 DB가 아닌 별도 검증 DB를 먼저 생성한 뒤 진행한다. `.env`만 바꾸어 기존 볼륨이 초기화될 것이라고 가정하지 않는다.

```bash
docker compose cp ./backup.dump db:/tmp/restore.dump
docker compose exec db sh -c 'createdb -U "$POSTGRES_USER" restore_check'
docker compose exec db sh -c 'pg_restore -U "$POSTGRES_USER" -d restore_check --no-owner /tmp/restore.dump'
```

검증 DB에 맞는 배포 버전으로 로그인·문서·업무 흐름을 확인한 뒤 전환한다. 첨부·문서 파일은 별도 볼륨이므로 DB 덤프만으로 복구되지 않는다. `docker compose down -v`는 유지보수 업데이트 명령이 아니라 데이터 삭제다.

## 설정과 LLM

기관명·코드는 `INSTITUTION_NAME/CODE`, 시연 계정 최초 비밀번호는 `DEMO_ACCOUNT_PASSWORD`, 로그인 제한은 `LOGIN_MAX_FAILURES/LOGIN_LOCK_DURATION`, 세션은 `SESSION_TIMEOUT/SESSION_COOKIE_SECURE`를 사용한다. 실제 값은 해당 버전 `.env.example`과 `application.yml`을 함께 확인한다. 최초 비밀번호 환경값 변경은 기존 계정 비밀번호 변경이 아니다.

[LLM 구성 변경](LLM_CONFIGURATION.md)을 따른다. 질문·기한·공개/비공개 지식은 관리자 게시 절차로 변경하고 기존 신청의 게시본을 직접 수정하지 않는다. SMTP는 GitHub·GitLab의 보안 수정 반영 여부에 따라 구현이 다를 수 있으므로 해당 버전 설정·코드를 확인한다.

## 개발 검사와 배포

```bash
python3 tools/check_doc_versions.py
./gradlew spotlessApply check
```

API 변경은 `docs/api/README.md`의 계약 추출·검사 절차를 따른다. Front 계약 사본과 함께 검토한다. 검토 후 원본에 새 태그를 만들고 통합 저장소의 스냅샷·RELEASE·해시 목록을 갱신한다. 개발 main 병합은 실행 중인 Docker를 자동 갱신하지 않는다. [공통 개발·배포 절차](../CONTRIBUTING.md)를 따른다.
