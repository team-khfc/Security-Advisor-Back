package kr.securityadvisor.document.service;

import java.util.List;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;

/**
 * 서류 출력 형식 하나를 맡는 변환기(F-06). 형식마다 구현 빈 하나 — 새 형식(HWPX 등)은 구현을 추가하면 자동으로 등록된다.
 * 출력은 저장된 서류(섹션·값·AI 보완)만 쓰고 업무 상태를 바꾸지 않는다.
 */
public interface DocumentExporter {

    ExportFormat format();

    /** 서류를 그 형식의 바이트로 만든다. 미확정 값은 숨기지 않고 "담당자 확인 필요"로 표시한다. */
    byte[] export(GeneratedDocument document, List<DocumentSection> sections);
}
