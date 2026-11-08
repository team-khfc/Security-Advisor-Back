package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.request.domain.FileContentCheck;
import kr.securityadvisor.request.domain.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** F-02: 이름만 바꾼 파일은 거부하고, 구 HWP 는 HWPX 변환 안내를 낸다. */
class FileContentCheckTest {

    @Test
    @DisplayName("PDF 서명·zip 내부 구조·UTF-8 텍스트가 확장자와 맞아야 통과")
    void matches() {
        assertThatCode(() -> FileContentCheck.requireMatches(
                        "pdf", "%PDF-1.7 synthetic".getBytes(StandardCharsets.US_ASCII)))
                .doesNotThrowAnyException();
        assertThatCode(() -> FileContentCheck.requireMatches(
                        "docx", zip(Map.of("[Content_Types].xml", "<Types/>", "word/document.xml", "<w:document/>"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> FileContentCheck.requireMatches(
                        "hwpx", zip(Map.of("mimetype", "application/hwp+zip", "Contents/section0.xml", "<hs:sec/>"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> FileContentCheck.requireMatches("txt", "합성 텍스트\n".getBytes(StandardCharsets.UTF_8)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이름만 .pdf 인 실행 파일·zip 아닌 docx·NUL 이 든 txt 는 422, .hwp 는 OLE 서명이 있어야 통과")
    void rejects() {
        byte[] exe = {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03};
        assertThatThrownBy(() -> FileContentCheck.requireMatches("pdf", exe))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("PDF 서명");
        assertThatThrownBy(
                        () -> FileContentCheck.requireMatches("docx", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("zip");
        assertThatThrownBy(() -> FileContentCheck.requireMatches(
                        "hwpx", zip(Map.of("[Content_Types].xml", "<Types/>", "word/document.xml", "<w:document/>"))))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("HWPX");
        assertThatThrownBy(() -> FileContentCheck.requireMatches("txt", new byte[] {'a', 0, 'b'}))
                .isInstanceOf(DomainRuleViolationException.class);
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        assertThatThrownBy(() -> FileContentCheck.requireMatches("pdf", ole))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("PDF 서명");
        assertThatCode(() -> FileContentCheck.requireMatches("hwp", ole)).doesNotThrowAnyException();
        assertThatThrownBy(() -> FileContentCheck.requireMatches("hwp", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(DomainRuleViolationException.class)
                .hasMessageContaining("HWP 5.0");
        assertThatCode(() -> UploadedFile.register(
                        1L, "계획서.hwp", "application/x-hwp", 10, "applicant", java.time.Instant.EPOCH))
                .doesNotThrowAnyException();
    }

    static byte[] zip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, String> e : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
