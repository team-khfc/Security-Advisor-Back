package kr.securityadvisor.document.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.service.DocumentExporter;
import org.springframework.stereotype.Component;

/** 기존 기관 검토 구성의 고정 제출값을 HWPX 문단으로 출력한다. 원본 문서·작성자 메타데이터는 포함하지 않는다. */
@Component
public class HwpxExporter implements DocumentExporter {
    public ExportFormat format() {
        return ExportFormat.HWPX;
    }

    public byte[] export(GeneratedDocument document, List<DocumentSection> sections) {
        try (var buffer = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            byte[] mime = "application/hwp+zip".getBytes(StandardCharsets.UTF_8);
            var entry = new ZipEntry("mimetype");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(mime.length);
            var crc = new CRC32();
            crc.update(mime);
            entry.setCrc(crc.getValue());
            zip.putNextEntry(entry);
            zip.write(mime);
            zip.closeEntry();
            put(zip, "version.xml", resource("version.xml"));
            put(zip, "Contents/header.xml", resource("header.xml"));
            put(zip, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <ocf:container xmlns:ocf="urn:oasis:names:tc:opendocument:xmlns:container"><ocf:rootfiles>
                <ocf:rootfile full-path="Contents/content.hpf" media-type="application/hwpml-package+xml"/>
                </ocf:rootfiles></ocf:container>
                """);
            put(zip, "Contents/content.hpf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <opf:package xmlns:opf="http://www.idpf.org/2007/opf/" version="" unique-identifier="" id="">
                <opf:metadata><opf:title>보안길잡이 검토 문서</opf:title><opf:language>ko</opf:language></opf:metadata>
                <opf:manifest><opf:item id="header" href="Contents/header.xml" media-type="application/xml"/>
                <opf:item id="section0" href="Contents/section0.xml" media-type="application/xml"/></opf:manifest>
                <opf:spine><opf:itemref idref="header" linear="yes"/><opf:itemref idref="section0" linear="yes"/></opf:spine></opf:package>
                """);
            List<String> lines = new ArrayList<>();
            lines.add(document.getTitle());
            lines.add(document.getKind().label() + " · 신청 #" + document.getSecurityRequestId());
            lines.add("서류 확정은 실제 장비 적용과 다릅니다. 기관 지정 고정 서식이 아닌 기본 검토 양식입니다.");
            for (var section : sections) {
                lines.add("■ " + section.title());
                for (var value : section.values()) {
                    lines.add(value.label());
                    Collections.addAll(lines, value.display().split("\\R", -1));
                    lines.add("확인 상태: " + value.status() + " · 출처: " + source(value.valueSource()));
                }
            }
            if (document.getAiSupplement() != null) {
                lines.add("AI 보완 · 미확정");
                Collections.addAll(lines, document.getAiSupplement().split("\\R", -1));
            }
            var xml = new StringBuilder(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><hs:sec xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\" xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\">");
            for (int i = 0; i < lines.size(); i++) {
                xml.append("<hp:p id=\"")
                        .append(i)
                        .append(
                                "\" paraPrIDRef=\"0\" styleIDRef=\"0\" pageBreak=\"0\" columnBreak=\"0\" merged=\"0\"><hp:run charPrIDRef=\"0\">");
                if (i == 0) xml.append(resource("section-properties.xml"));
                xml.append("<hp:t>").append(escape(lines.get(i))).append("</hp:t></hp:run></hp:p>");
            }
            put(zip, "Contents/section0.xml", xml.append("</hs:sec>").toString());
            put(zip, "Preview/PrvText.txt", String.join("\n", lines));
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("HWPX 문서 생성 실패", e);
        }
    }

    private static String source(String source) {
        if (source == null) return "기록";
        return switch (source) {
            case "USER_ANSWER" -> "사용자 답변";
            case "AI_INFERRED" -> "AI 추론";
            case "STAFF_INPUT" -> "담당자 입력";
            case "STAFF_CONFIRMED" -> "담당자 확인";
            default -> source;
        };
    }

    private static String resource(String name) throws IOException {
        try (var in = HwpxExporter.class.getResourceAsStream("/document/hwpx/" + name)) {
            if (in == null) throw new IOException("출력 서식 누락");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String escape(String s) {
        return s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static void put(ZipOutputStream zip, String path, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
