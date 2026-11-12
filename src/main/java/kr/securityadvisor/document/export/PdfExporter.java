package kr.securityadvisor.document.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.dto.DocumentValue;
import kr.securityadvisor.document.service.DocumentExporter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * PDF 출력(F-06). PDFBox 로 A4 세로 한 단 조판 — 제목, 생성 정보, 섹션별 "항목 / 값 / 출처·확인" 표, AI 보완 서술(미확정 표시),
 * 바닥글(쪽 번호·합성 데이터 표기). 한글은 번들 서브셋 글꼴(Noto Sans CJK KR, OFL)을 부분 집합으로 임베드한다.
 */
@Component
public class PdfExporter implements DocumentExporter {

    private static final String FONT_PATH = "fonts/NotoSansCJKkr-Regular-subset.ttf";
    private static final float MARGIN = 50f;
    private static final float TITLE_SIZE = 16f;
    private static final float HEADING_SIZE = 12f;
    private static final float BODY_SIZE = 10f;
    private static final float SMALL_SIZE = 8f;
    private static final float LEADING = 1.45f;
    private static final float LABEL_WIDTH = 150f;
    private static final float SOURCE_WIDTH = 95f;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"));

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public byte[] export(GeneratedDocument document, List<DocumentSection> sections) {
        try (PDDocument pdf = new PDDocument()) {
            PDFont font;
            try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
                font = PDType0Font.load(pdf, in, true);
            }
            Writer w = new Writer(pdf, font);
            w.text(document.getTitle(), TITLE_SIZE);
            w.gap(4);
            w.text(
                    document.getKind().label() + " v" + document.getVersion() + " · 신청 #"
                            + document.getSecurityRequestId()
                            + (document.getSubmissionId() == null ? "" : " · 제출본 " + document.getSubmissionId())
                            + " · 생성 " + TIME.format(document.getGeneratedAt()) + " (" + document.getGeneratedBy()
                            + ")",
                    SMALL_SIZE);
            if (document.getUnconfirmedCount() > 0) {
                w.text("※ 담당자 확인 필요 항목 " + document.getUnconfirmedCount() + "건 — 확정 서류가 아닙니다", SMALL_SIZE);
            }
            w.gap(10);
            for (DocumentSection section : sections) {
                w.heading(section.title());
                for (DocumentValue value : section.values()) {
                    if ("report.finalSummary".equals(value.fieldKey())
                            || value.fieldKey().startsWith("preparation.")) {
                        if (value.fieldKey().startsWith("preparation.")) w.text(value.label(), HEADING_SIZE);
                        w.text(sourceLabel(value) + " · " + value.status(), SMALL_SIZE);
                        for (String line : value.display().split("\\R")) w.text(line, BODY_SIZE);
                    } else {
                        w.row(value.label(), value.display(), sourceLabel(value));
                    }
                }
                w.gap(8);
            }
            if (document.getAiSupplement() != null
                    && !document.getAiSupplement().isBlank()) {
                w.heading("AI 보완 서술 (미확정 — 사용자 확인 전)");
                for (String line : document.getAiSupplement().split("\\R")) {
                    w.text(line, BODY_SIZE);
                }
                w.text(
                        "모델: " + (document.getModelReference() == null ? "-" : document.getModelReference()),
                        SMALL_SIZE);
            }
            w.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF 생성 실패", e);
        }
    }

    private static String sourceLabel(DocumentValue value) {
        if (value.fieldKey().startsWith("preparation.")) return "자료 현황 기록";
        if (value.valueSource() == null) {
            return "출처 없음";
        }
        String source = switch (value.valueSource()) {
            case "USER_ANSWER" -> "사용자 답변";
            case "AI_INFERRED" -> "AI 추론";
            case "STAFF_INPUT" -> "담당자 입력";
            case "STAFF_CONFIRMED" -> "담당자 확인";
            default -> value.valueSource();
        };
        return source + (value.confirmed() ? " · 확인" : " · 미확정") + (value.needsReview() ? " · 재검토" : "");
    }

    /** 한 페이지씩 위에서 아래로 쓰는 간단한 조판기. 글줄 바꿈은 글꼴 폭 기준, 쪽이 차면 새 쪽. */
    private static final class Writer {
        private final PDDocument pdf;
        private final PDFont font;
        private final float width;
        private PDPageContentStream content;
        private float y;
        private int pageNo;

        Writer(PDDocument pdf, PDFont font) throws IOException {
            this.pdf = pdf;
            this.font = font;
            this.width = PDRectangle.A4.getWidth() - 2 * MARGIN;
            newPage();
        }

        void text(String text, float size) throws IOException {
            for (String line : wrap(text, size, width)) {
                ensure(size * LEADING);
                draw(line, MARGIN, size);
                y -= size * LEADING;
            }
        }

        void heading(String title) throws IOException {
            ensure(HEADING_SIZE * LEADING * 2);
            y -= 4;
            draw(title, MARGIN, HEADING_SIZE);
            y -= HEADING_SIZE * LEADING;
            content.setLineWidth(0.5f);
            content.moveTo(MARGIN, y + 3);
            content.lineTo(MARGIN + width, y + 3);
            content.stroke();
            y -= 14;
        }

        /** 항목 / 값 / 출처 3열. 값이 여러 줄이면 줄 수만큼 높이를 쓴다. */
        void row(String label, String value, String source) throws IOException {
            float valueWidth = width - LABEL_WIDTH - SOURCE_WIDTH - 10;
            List<String> labelLines = wrap(label, BODY_SIZE, LABEL_WIDTH - 5);
            List<String> valueLines = wrap(value, BODY_SIZE, valueWidth);
            List<String> sourceLines = wrap(source, SMALL_SIZE, SOURCE_WIDTH);
            int lines = Math.max(labelLines.size(), Math.max(valueLines.size(), sourceLines.size()));
            float height = lines * BODY_SIZE * LEADING;
            ensure(height + 2);
            for (int i = 0; i < lines; i++) {
                float lineY = y - i * BODY_SIZE * LEADING;
                if (i < labelLines.size()) {
                    drawAt(labelLines.get(i), MARGIN, lineY, BODY_SIZE);
                }
                if (i < valueLines.size()) {
                    drawAt(valueLines.get(i), MARGIN + LABEL_WIDTH, lineY, BODY_SIZE);
                }
                if (i < sourceLines.size()) {
                    drawAt(sourceLines.get(i), MARGIN + LABEL_WIDTH + valueWidth + 10, lineY, SMALL_SIZE);
                }
            }
            y -= height;
            content.setLineWidth(0.2f);
            content.moveTo(MARGIN, y + 4);
            content.lineTo(MARGIN + width, y + 4);
            content.stroke();
            y -= 10;
        }

        void gap(float points) {
            y -= points;
        }

        void close() throws IOException {
            footer();
            content.close();
        }

        private void ensure(float needed) throws IOException {
            if (y - needed < MARGIN + 20) {
                footer();
                content.close();
                newPage();
            }
        }

        private void newPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            content = new PDPageContentStream(pdf, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
            pageNo++;
        }

        private void footer() throws IOException {
            String left = "보안길잡이(SecurityAdvisor) — 합성 시연 데이터 기준, 서류 확정 ≠ 방화벽 적용";
            String right = pageNo + " 쪽";
            drawAt(left, MARGIN, MARGIN - 15, SMALL_SIZE);
            float rightWidth = font.getStringWidth(right) / 1000 * SMALL_SIZE;
            drawAt(right, MARGIN + width - rightWidth, MARGIN - 15, SMALL_SIZE);
        }

        private void draw(String text, float x, float size) throws IOException {
            drawAt(text, x, y, size);
        }

        private void drawAt(String text, float x, float atY, float size) throws IOException {
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(sanitize(text));
            content.endText();
        }

        /** 글꼴에 없는 문자(제어 문자·이모지 등)는 '□'로 바꿔 PDFBox 가 예외를 내지 않게 한다. */
        private String sanitize(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (cp == '\t') {
                    sb.append("    ");
                } else if (cp < 0x20) {
                    sb.append(' ');
                } else if (hasGlyph(cp)) {
                    sb.appendCodePoint(cp);
                } else {
                    sb.append('□');
                }
            });
            return sb.toString();
        }

        private boolean hasGlyph(int codePoint) {
            try {
                font.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        }

        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                lines.add("");
                return lines;
            }
            for (String paragraph : text.split("\\R")) {
                StringBuilder current = new StringBuilder();
                for (int i = 0; i < paragraph.length(); ) {
                    int cp = paragraph.codePointAt(i);
                    String ch = sanitize(new String(Character.toChars(cp)));
                    if (textWidth(current + ch, size) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(ch);
                    i += Character.charCount(cp);
                }
                lines.add(current.toString());
            }
            return lines;
        }

        private float textWidth(String text, float size) throws IOException {
            return font.getStringWidth(text) / 1000 * size;
        }
    }
}
