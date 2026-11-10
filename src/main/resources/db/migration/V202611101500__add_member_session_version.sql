-- 기존 계정의 권한 세대는 0으로 시작한다. 권한 변경 이후 옛 세션의 재사용을 차단한다.
-- 기존 업무 데이터는 변경하지 않는다. 복구 시 컬럼을 보존한 채 이전 앱으로 되돌릴 수 있다.
ALTER TABLE members ADD COLUMN session_version bigint NOT NULL DEFAULT 0;
