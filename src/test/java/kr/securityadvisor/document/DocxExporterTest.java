package kr.securityadvisor.document;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.document.domain.*;
import kr.securityadvisor.document.dto.*;
import kr.securityadvisor.document.export.DocxExporter;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxExporterTest {
    @Test
    void 기관_검토용_표에_출처와_미확정_상태를_보존한다() throws Exception {
        var document = GeneratedDocument.render(
                1L, 2L, DocumentKind.SUBMITTED, 1, "합성 보안성검토", "[]", "본문", null, null, 1, "synthetic", Instant.EPOCH);
        var sections = List.of(new DocumentSection(
                "담당자 검토",
                List.of(new DocumentValue(
                        "x", "기간", "REQUEST", "합성 값", "AI_INFERRED", false, false, "합성 값", "담당자 확인 필요"))));
        byte[] bytes = new DocxExporter().export(document, sections);
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes));
                var reader = new XWPFWordExtractor(doc)) {
            assertThat(reader.getText()).contains("합성 보안성검토", "담당자 확인 필요", "AI 추론", "합성 값");
            assertThat(doc.getTables()).hasSize(1);
        }
    }
}
