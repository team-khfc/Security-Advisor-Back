package kr.securityadvisor.llm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

/**
 * 구 한글(HWP 5.0, OLE 복합문서) 본문 텍스트 추출. 공개 규격(한글과컴퓨터 "HWP 5.0 파일 형식")대로
 * FileHeader 의 속성 플래그 → BodyText/Section N 스트림(zlib raw deflate) → 레코드 헤더(태그 10비트·레벨 10비트·크기 12비트) →
 * HWPTAG_PARA_TEXT(67) 의 UTF-16LE 문자열에서 제어 문자를 걷어낸다. 표·그림 안의 텍스트도 같은 태그라 함께 나온다.
 *
 * <p>암호 문서·배포용 문서(DRM)는 복호화가 필요해 지원하지 않는다 — 안내 메시지로 거부한다. 서식·표 구조는 복원하지 않는다(텍스트만).
 * 실제 한글 산출물로 검증해야 한다(단위 테스트는 규격대로 합성한 파일을 쓴다).
 */
public final class HwpTextExtractor {

    public static final String UNSUPPORTED_GUIDE =
            "암호가 걸렸거나 배포용으로 저장된 HWP 는 읽을 수 없습니다. 한글에서 '다른 이름으로 저장 → HWPX' 로 변환해 올려 주세요";

    private static final String SIGNATURE = "HWP Document File";
    private static final int TAG_PARA_TEXT = 0x10 + 51;
    private static final int FLAG_COMPRESSED = 1;
    private static final int FLAG_PASSWORD = 1 << 1;
    private static final int FLAG_DISTRIBUTION = 1 << 2;

    private HwpTextExtractor() {}

    public static String extract(InputStream in) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(in)) {
            DirectoryEntry root = fs.getRoot();
            byte[] header = read(root, "FileHeader");
            if (header.length < 40
                    || !new String(header, 0, SIGNATURE.length(), StandardCharsets.US_ASCII).equals(SIGNATURE)) {
                throw new IOException("HWP 5.0 파일 서명이 아닙니다");
            }
            int flags = ByteBuffer.wrap(header, 36, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();
            if ((flags & FLAG_PASSWORD) != 0 || (flags & FLAG_DISTRIBUTION) != 0) {
                throw new IOException(UNSUPPORTED_GUIDE);
            }
            boolean compressed = (flags & FLAG_COMPRESSED) != 0;
            if (!root.hasEntry("BodyText")) {
                throw new IOException("HWP 본문(BodyText)이 없습니다");
            }
            DirectoryEntry body = (DirectoryEntry) root.getEntry("BodyText");
            Map<Integer, byte[]> sections = new TreeMap<>();
            for (Entry entry : body) {
                if (entry.getName().startsWith("Section") && entry instanceof DocumentEntry) {
                    byte[] raw = read(body, entry.getName());
                    sections.put(
                            Integer.parseInt(entry.getName().substring("Section".length())),
                            compressed ? inflate(raw) : raw);
                }
            }
            StringBuilder out = new StringBuilder();
            for (byte[] section : sections.values()) {
                paragraphs(section, out);
            }
            return out.toString();
        }
    }

    private static byte[] read(DirectoryEntry dir, String name) throws IOException {
        Entry entry = dir.getEntry(name);
        if (!(entry instanceof DocumentEntry document)) {
            throw new IOException("HWP 스트림이 아닙니다: " + name);
        }
        try (DocumentInputStream stream = new DocumentInputStream(document)) {
            return stream.readAllBytes();
        }
    }

    private static byte[] inflate(byte[] data) throws IOException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(data);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, data.length * 4))) {
            byte[] buffer = new byte[8192];
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("HWP 본문 압축을 풀 수 없습니다", e);
        } finally {
            inflater.end();
        }
    }

    /** 레코드를 훑어 PARA_TEXT 마다 한 문단을 덧붙인다. */
    private static void paragraphs(byte[] section, StringBuilder out) {
        ByteBuffer buf = ByteBuffer.wrap(section).order(ByteOrder.LITTLE_ENDIAN);
        while (buf.remaining() >= 4) {
            int header = buf.getInt();
            int tag = header & 0x3FF;
            int size = (header >>> 20) & 0xFFF;
            if (size == 0xFFF) {
                if (buf.remaining() < 4) {
                    return;
                }
                size = buf.getInt();
            }
            if (size < 0 || size > buf.remaining()) {
                return; // 손상된 레코드 — 지금까지 읽은 것만 돌려준다
            }
            if (tag == TAG_PARA_TEXT) {
                String text = paragraphText(buf, size);
                if (!text.isBlank()) {
                    out.append(text).append('\n');
                }
            } else {
                buf.position(buf.position() + size);
            }
        }
    }

    /**
     * UTF-16LE 문자열에서 제어 문자를 걷어낸다: 0·10·13·24~31 은 1자 제어(줄바꿈 등), 1~9·11·12·14~23 은 8자(16바이트)짜리 인라인/확장 제어.
     */
    private static String paragraphText(ByteBuffer buf, int size) {
        int end = buf.position() + size;
        StringBuilder sb = new StringBuilder();
        while (buf.position() + 1 < end) {
            char c = buf.getChar();
            if (c == 13 || c == 10) {
                sb.append('\n');
            } else if (c < 32) {
                if (c != 0 && !(c >= 24 && c <= 31)) {
                    int skip = Math.min(14, end - buf.position());
                    buf.position(buf.position() + skip);
                }
            } else {
                sb.append(c);
            }
        }
        buf.position(end);
        return sb.toString().strip();
    }
}
