package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import kr.securityadvisor.llm.service.TextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** HWPX(OWPML) 본문에서 문단 텍스트를 섹션 번호순으로 뽑고, 외부 엔티티(XXE)는 해석하지 않는다. */
class TextExtractorTest {

    private final TextExtractor extractor = new TextExtractor();

    @Test
    @DisplayName("section 번호순·문단별 줄바꿈·hp:t 이어 붙이기")
    void hwpx() throws Exception {
        String ns =
                "xmlns:hp=\"http://www.hancom.co.kr/hwpml/2011/paragraph\" xmlns:hs=\"http://www.hancom.co.kr/hwpml/2011/section\"";
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/hwp+zip");
        entries.put(
                "Contents/section1.xml",
                "<?xml version=\"1.0\"?><hs:sec " + ns + "><hp:p><hp:run><hp:t>둘째 섹션</hp:t></hp:run></hp:p></hs:sec>");
        entries.put(
                "Contents/section0.xml",
                "<?xml version=\"1.0\"?><hs:sec " + ns
                        + "><hp:p><hp:run><hp:t>합성 사업</hp:t></hp:run><hp:run><hp:t>계획서</hp:t></hp:run></hp:p>"
                        + "<hp:p><hp:run><hp:t>외부 API 연동 민원 포털(합성)</hp:t></hp:run></hp:p></hs:sec>");
        String text = extractor.extract("hwpx", new ByteArrayInputStream(zip(entries)));
        assertThat(text).isEqualTo("합성 사업계획서\n외부 API 연동 민원 포털(합성)\n둘째 섹션");
    }

    @Test
    @DisplayName("DOCTYPE·외부 엔티티가 든 HWPX 는 거부(XXE 차단), 본문 없는 zip 도 거부")
    void rejectsXxeAndEmpty() {
        Map<String, String> xxe = Map.of(
                "Contents/section0.xml",
                "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/hostname\">]><sec><p><t>&e;</t></p></sec>");
        assertThatThrownBy(() -> extractor.extract("hwpx", new ByteArrayInputStream(zip(xxe))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> extractor.extract("hwpx", new ByteArrayInputStream(zip(Map.of("mimetype", "x")))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("section");
    }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
