#!/usr/bin/env python3
"""문서 기준 버전, 단일 경로, 비식별 공개본의 무결성을 점검한다."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import sys


DOCUMENT_PATHS = {
    "INSTRUCTIONS.md": Path("claude/INSTRUCTIONS.md"),
    "PROJECT_CONTEXT.md": Path("PROJECT_CONTEXT.md"),
    "FUNCTIONAL_SPEC.md": Path("FUNCTIONAL_SPEC.md"),
    "AGENTS.md": Path("AGENTS.md"),
    "PROJECT_RULES.md": Path("PROJECT_RULES.md"),
    "CONTRIBUTING.md": Path("CONTRIBUTING.md"),
    "CODING_STYLE.md": Path("CODING_STYLE.md"),
    "SECURE_CODING.md": Path("SECURE_CODING.md"),
    "design.md": Path("design.md"),
}
EXCLUDED_DIRECTORIES = {".git", ".next", "node_modules", "coverage", "dist", "build"}
PROVENANCE_PATH = Path("docs/SOURCE_PROVENANCE.md")
EXAMPLE_DOMAINS = {"example.com", "example.org", "example.net"}
PUBLIC_CONTACT_EMAILS = {"databus@nia.or.kr"}
PUBLIC_CONTACT_PHONES = {"053-230-1984"}
EMAIL_PATTERN = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
PHONE_PATTERN = re.compile(r"(?<!\d)0\d{1,2}-\d{3,4}-\d{4}(?!\d)")


def table_rows(text: str) -> list[list[str]]:
    """열 수가 다른 일반 표도 처리하되 호출자가 필요한 행만 선택한다."""
    return [
        [cell.strip().strip("`") for cell in line.strip().strip("|").split("|")]
        for line in text.splitlines()
        if line.lstrip().startswith("|")
    ]


def expected_versions(context: str) -> dict[str, tuple[str, str]]:
    """공식 기준 표만 읽어 다른 장에 인용된 이전 버전과 구별한다."""
    section = re.search(r"^## 5\..*?\n(.*?)(?=^## |\Z)", context, re.MULTILINE | re.DOTALL)
    if section is None:
        return {}
    expected = {}
    for row in table_rows(section.group(1)):
        if len(row) >= 3 and row[0] in DOCUMENT_PATHS:
            expected[row[0]] = (row[1].lstrip("v"), row[2])
    return expected


def header_version(text: str) -> tuple[str | None, str | None]:
    """머리말 첫 버전과 개정일을 추출하고 개정 이력의 이전 판본은 사용하지 않는다."""
    header = "\n".join(text.splitlines()[:8])
    design_version = re.search(r"문서 버전:\s*\*\*(\d+\.\d+)\*\*", header)
    normal_version = re.search(r"(?<![\w.])v(\d+\.\d+)(?![\w.])", header)
    version = design_version or normal_version
    date = re.search(r"\b\d{4}-\d{2}-\d{2}\b", header)
    return (version.group(1) if version else None, date.group(0) if date else None)


def find_document_paths(root: Path) -> dict[str, list[Path]]:
    """생성물과 의존성 디렉터리는 탐색하지 않아 공급 파일을 사본으로 오인하지 않는다."""
    canonical_names = {name.casefold(): name for name in DOCUMENT_PATHS}
    found: dict[str, list[Path]] = {name: [] for name in DOCUMENT_PATHS}
    for parent, directories, filenames in os.walk(root):
        directories[:] = [name for name in directories if name not in EXCLUDED_DIRECTORIES]
        for filename in filenames:
            canonical = canonical_names.get(filename.casefold())
            if canonical:
                found[canonical].append((Path(parent) / filename).relative_to(root))
    return found


def public_hashes(provenance: str) -> dict[str, str]:
    """개인정보가 제거된 공개본 해시만 점검하며 첨부 원본을 저장소에 요구하지 않는다."""
    hashes = {}
    for row in table_rows(provenance):
        if len(row) >= 6 and row[0] in DOCUMENT_PATHS and re.fullmatch(r"[0-9a-f]{64}", row[5]):
            hashes[row[0]] = row[5]
    return hashes


def contact_errors(path: Path, text: str) -> list[str]:
    """검사 메시지에는 발견한 연락처 원문을 출력하지 않는다."""
    errors = []
    for number, line in enumerate(text.splitlines(), start=1):
        for match in EMAIL_PATTERN.finditer(line):
            address = match.group(0).lower()
            domain = address.rsplit("@", 1)[1]
            if address not in PUBLIC_CONTACT_EMAILS and domain not in EXAMPLE_DOMAINS:
                errors.append(f"{path}:{number}: 공개본에 허용되지 않은 이메일 형식이 있습니다.")
        for match in PHONE_PATTERN.finditer(line):
            if match.group(0) not in PUBLIC_CONTACT_PHONES:
                errors.append(f"{path}:{number}: 공개본에 허용되지 않은 전화번호 형식이 있습니다.")
    return errors


def check(root: Path) -> tuple[list[str], list[str]]:
    """버전·경로·공개본 해시와 명시된 범위의 연락처 형식을 함께 검사한다."""
    errors: list[str] = []
    checked: list[str] = []
    context_path = root / DOCUMENT_PATHS["PROJECT_CONTEXT.md"]
    if not context_path.is_file():
        return ["PROJECT_CONTEXT.md가 없어 기준 버전을 확인할 수 없습니다."], checked
    context = context_path.read_text(encoding="utf-8")
    expected = expected_versions(context)
    paths = find_document_paths(root)
    provenance_path = root / PROVENANCE_PATH
    hashes = public_hashes(provenance_path.read_text(encoding="utf-8")) if provenance_path.is_file() else {}
    if not provenance_path.is_file():
        errors.append(f"{PROVENANCE_PATH}: 공개본 변환 기록이 없습니다.")

    for name, relative in DOCUMENT_PATHS.items():
        if name not in expected:
            errors.append(f"PROJECT_CONTEXT.md 5장에 {name}의 기준 버전이 없습니다.")
        if paths[name] != [relative]:
            locations = ", ".join(str(item) for item in paths[name]) or "없음"
            errors.append(f"{name}: 정해진 경로는 {relative} 한 곳입니다. 발견: {locations}")
        path = root / relative
        if not path.is_file():
            continue
        if path.is_symlink():
            errors.append(f"{relative}: 규칙 문서는 심볼릭 링크가 아닌 실제 파일이어야 합니다.")
        raw = path.read_bytes()
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            errors.append(f"{relative}: UTF-8 문서가 아닙니다.")
            continue
        actual = header_version(text)
        if name in expected and actual != expected[name]:
            errors.append(f"{relative}: 머리말 {actual}, 기준 표 {expected[name]}가 다릅니다.")
        errors.extend(contact_errors(relative, text))
        if name != "INSTRUCTIONS.md":
            expected_hash = hashes.get(name)
            if expected_hash is None:
                errors.append(f"{PROVENANCE_PATH}: {name}의 공개본 해시가 없습니다.")
            elif hashlib.sha256(raw).hexdigest() != expected_hash:
                errors.append(f"{relative}: 공개본 해시가 변환 기록과 다릅니다. 사본·개정 절차를 확인하세요.")
        if name in expected:
            checked.append(f"{relative}: v{expected[name][0]} · {expected[name][1]}")
    return errors, checked


def main() -> int:
    """실제 저장소를 기본 대상으로 삼으며 필요할 때 명시한 루트를 점검한다."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        errors, checked = check(args.root.resolve())
    except OSError as error:
        print(f"FAIL: 문서를 읽을 수 없습니다 ({type(error).__name__}).", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        print(f"문서 점검 실패: {len(errors)}개 항목", file=sys.stderr)
        return 1
    for result in checked:
        print(f"PASS: {result}")
    print(f"문서 {len(checked)}종의 버전·단일 경로·공개본 해시·연락처 형식 점검 통과")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
