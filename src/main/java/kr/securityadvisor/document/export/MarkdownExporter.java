package kr.securityadvisor.document.export;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.service.DocumentExporter;
import org.springframework.stereotype.Component;

/** Markdown 출력 — 생성 시 렌더링해 둔 본문을 그대로 낸다. */
@Component
public class MarkdownExporter implements DocumentExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.MARKDOWN;
    }

    @Override
    public byte[] export(GeneratedDocument document, List<DocumentSection> sections) {
        return document.getRenderedMarkdown().getBytes(StandardCharsets.UTF_8);
    }
}
