package kr.securityadvisor.document;

import static org.assertj.core.api.Assertions.*;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import kr.securityadvisor.document.domain.*;
import kr.securityadvisor.document.dto.*;
import kr.securityadvisor.document.export.HwpxExporter;
import kr.securityadvisor.llm.service.TextExtractor;
import org.junit.jupiter.api.Test;

class HwpxExporterTest {
    @Test
    void 출력은_원문과_메타데이터를_포함하지_않고_긴_값과_확인상태를_보존한다() throws Exception {
        var document = GeneratedDocument.render(
                1L, 2L, DocumentKind.SUBMITTED, 1, "합성 검토 <&>", "[]", "본문", null, null, 1, "synthetic", Instant.EPOCH);
        String longValue = "합성 내용 & < > ".repeat(800);
        var sections = List.of(new DocumentSection(
                "검토 결과",
                List.of(new DocumentValue(
                        "x", "조건", "REQUEST", longValue, "AI_INFERRED", false, false, longValue, "담당자 확인 필요"))));
        byte[] bytes = new HwpxExporter().export(document, sections);
        var entries = new HashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            var first = zip.getNextEntry();
            assertThat(first.getName()).isEqualTo("mimetype");
            assertThat(first.getMethod()).isEqualTo(ZipEntry.STORED);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
        }
        var parser = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        parser.setNamespaceAware(true);
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        for (var entry : entries.entrySet())
            if (entry.getKey().endsWith(".xml") || entry.getKey().endsWith(".hpf"))
                parser.newDocumentBuilder().parse(new ByteArrayInputStream(entry.getValue()));
        assertThat(new TextExtractor().extractFull("hwpx", new ByteArrayInputStream(bytes)))
                .contains("합성 검토 <&>", longValue, "AI 추론", "담당자 확인 필요");
        assertThat(new String(entries.get("Contents/content.hpf"), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("pocke", "lastsaveby");
    }
}
