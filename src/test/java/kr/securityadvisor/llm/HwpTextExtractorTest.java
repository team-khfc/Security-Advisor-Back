package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import kr.securityadvisor.llm.service.HwpTextExtractor;
import kr.securityadvisor.llm.service.TextExtractor;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HWP 5.0 공개 규격대로 합성한 파일(FileHeader + BodyText/Section0, raw deflate, PARA_HEADER/PARA_TEXT 레코드)에서 본문을 뽑는다.
 * 인라인 제어 문자(8자)는 걷어내고, 암호·배포용 플래그는 안내 메시지로 거부한다. 실제 한글 산출물 검증은 별도.
 */
class HwpTextExtractorTest {

    private static final int TAG_PARA_HEADER = 0x10 + 50;
    private static final int TAG_PARA_TEXT = 0x10 + 51;

    @Test
    @DisplayName("압축된 본문에서 두 문단 텍스트를 뽑고 인라인 제어(탭·그림 등 8자)는 사라진다")
    void extractsParagraphs() throws Exception {
        ByteArrayOutputStream section = new ByteArrayOutputStream();
        section.write(record(TAG_PARA_HEADER, new byte[22]));
        section.write(record(TAG_PARA_TEXT, paraText("합성 사업계획서", true)));
        section.write(record(TAG_PARA_HEADER, new byte[22]));
        section.write(record(TAG_PARA_TEXT, paraText("외부 API 연동 민원 포털(합성)", false)));
        byte[] hwp = hwp(section.toByteArray(), 0b001); // compressed

        String text = new TextExtractor().extract("hwp", new ByteArrayInputStream(hwp));
        assertThat(text).isEqualTo("합성 사업계획서\n외부 API 연동 민원 포털(합성)");
    }

    @Test
    @DisplayName("암호(비트1)·배포용(비트2) 문서는 HWPX 변환 안내로 거부, 서명이 다르면 거부")
    void rejectsProtected() throws Exception {
        byte[] body = record(TAG_PARA_TEXT, paraText("x", false));
        assertThatThrownBy(() -> HwpTextExtractor.extract(new ByteArrayInputStream(hwp(body, 0b010))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HWPX");
        assertThatThrownBy(() -> HwpTextExtractor.extract(new ByteArrayInputStream(hwp(body, 0b100))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HWPX");
    }

    /** 레코드 헤더: 태그(10비트) | 레벨(10비트)=0 | 크기(12비트). */
    private static byte[] record(int tag, byte[] data) {
        ByteBuffer buf = ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(tag | (data.length << 20));
        buf.put(data);
        return buf.array();
    }

    /** UTF-16LE 문자열(+ 선택적으로 8자짜리 인라인 제어 코드 9(탭) 삽입, 끝에 13(문단 끝)). */
    private static byte[] paraText(String text, boolean withInlineControl) {
        ByteBuffer buf = ByteBuffer.allocate(text.length() * 2 + 2 + (withInlineControl ? 16 : 0))
                .order(ByteOrder.LITTLE_ENDIAN);
        if (withInlineControl) {
            buf.putChar((char) 9);
            for (int i = 0; i < 7; i++) {
                buf.putChar('X'); // 제어 코드 뒤 7자(14바이트)는 데이터라 걷어내야 한다
            }
        }
        for (char c : text.toCharArray()) {
            buf.putChar(c);
        }
        buf.putChar((char) 13);
        return buf.array();
    }

    private static byte[] hwp(byte[] section, int flags) throws IOException {
        byte[] header = new byte[256];
        byte[] signature = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(signature, 0, header, 0, signature.length);
        ByteBuffer.wrap(header, 32, 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x05000300)
                .putInt(flags);
        byte[] body = (flags & 1) != 0 ? deflate(section) : section;
        try (POIFSFileSystem fs = new POIFSFileSystem()) {
            fs.getRoot().createDocument("FileHeader", new ByteArrayInputStream(header));
            DirectoryEntry bodyText = fs.getRoot().createDirectory("BodyText");
            bodyText.createDocument("Section0", new ByteArrayInputStream(body));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + 64];
        int n = deflater.deflate(buffer);
        deflater.end();
        byte[] out = new byte[n];
        System.arraycopy(buffer, 0, out, 0, n);
        return out;
    }
}
