package kr.securityadvisor.request.domain;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import kr.securityadvisor.common.error.DomainRuleViolationException;

/**
 * 업로드 파일의 실제 내용이 확장자와 맞는지 검사한다(F-02, SECURE_CODING "허용 목록 검증" — 이름만 .pdf 인 실행 파일 차단).
 * 바이트 서명과 컨테이너(zip) 내부 구조를 본다. 외부 라이브러리 없이 표준 API 만 쓴다.
 */
public final class FileContentCheck {

    /** 구 한글(HWP 5.0) 파일 안내. OLE 복합문서 서명으로 식별한다. */
    public static final String HWP_GUIDE = "HWP(구 한글 형식)는 지원하지 않습니다. 한글에서 '다른 이름으로 저장 → HWPX' 로 변환해 올려 주세요";

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE = {
        (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };
    private static final int TEXT_PROBE = 8 * 1024;

    private FileContentCheck() {}

    /**
     * @throws DomainRuleViolationException 내용이 확장자와 맞지 않을 때(422). 메시지에 내부 정보는 없다
     */
    public static void requireMatches(String extension, byte[] content) {
        if (content == null || content.length == 0) {
            throw new DomainRuleViolationException("파일 내용이 비어 있습니다");
        }
        if (startsWith(content, OLE)) {
            throw new DomainRuleViolationException(HWP_GUIDE);
        }
        switch (extension) {
            case "pdf" -> require(startsWith(content, PDF), extension, "PDF 서명(%PDF-)이 없습니다");
            case "docx" -> {
                Set<String> entries = zipEntries(content, extension);
                require(
                        entries.contains("[Content_Types].xml")
                                && entries.stream().anyMatch(e -> e.startsWith("word/")),
                        extension,
                        "Word(OOXML) 구조가 아닙니다");
            }
            case "hwpx" -> {
                Set<String> entries = zipEntries(content, extension);
                require(
                        entries.stream().anyMatch(e -> e.matches("Contents/section\\d+\\.xml"))
                                || entries.contains("Contents/content.hpf"),
                        extension,
                        "HWPX(OWPML) 구조가 아닙니다");
            }
            case "txt", "md" -> require(looksLikeUtf8Text(content), extension, "UTF-8 텍스트가 아닙니다");
            default -> throw new DomainRuleViolationException("허용되지 않는 파일 형식입니다(" + extension + ")");
        }
    }

    private static void require(boolean ok, String extension, String why) {
        if (!ok) {
            throw new DomainRuleViolationException("파일 내용이 확장자(" + extension + ")와 맞지 않습니다: " + why);
        }
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static Set<String> zipEntries(byte[] content, String extension) {
        require(startsWith(content, ZIP), extension, "zip 컨테이너가 아닙니다");
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null && names.size() < 2000) {
                names.add(entry.getName());
                zip.closeEntry();
            }
        } catch (IOException e) {
            require(false, extension, "zip 을 읽을 수 없습니다");
        }
        return names;
    }

    private static boolean looksLikeUtf8Text(byte[] content) {
        int length = Math.min(content.length, TEXT_PROBE);
        for (int i = 0; i < length; i++) {
            if (content[i] == 0) {
                return false;
            }
        }
        try {
            // 끝이 잘린 다중 바이트 문자는 무시하고, 잘못된 바이트 열만 거른다
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, 0, length - (length == TEXT_PROBE ? 4 : 0)));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
