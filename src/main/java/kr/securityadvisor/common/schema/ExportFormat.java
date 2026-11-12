package kr.securityadvisor.common.schema;

import java.util.Locale;
import java.util.Optional;

/**
 * 서류 출력 형식(F-06). 게시본의 서류 유형 정의({@link DocTypeDefinition#exportFormats()})가 허용 형식을 정하고,
 * 실제 변환은 document 컨텍스트의 {@code DocumentExporter} 구현이 형식마다 하나씩 맡는다. 새 형식(HWPX 등)은 여기에 값을 더하고
 * 구현을 추가하면 된다 — 구현이 없는 형식은 "미구현"으로 조회되고 다운로드는 거부된다.
 */
public enum ExportFormat {
    MARKDOWN("md", "text/markdown;charset=UTF-8"),
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    /** 한글 문서(HWPX, KS X 6101). 1차 범위 미구현 — 형식 목록에만 둔다 */
    HWPX("hwpx", "application/hwp+zip");

    private final String extension;
    private final String mediaType;

    ExportFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<ExportFormat> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
