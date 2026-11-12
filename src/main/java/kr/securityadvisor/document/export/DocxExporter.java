package kr.securityadvisor.document.export;

import java.io.*;
import java.util.List;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.service.DocumentExporter;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

/** 고정된 제출 문서의 항목·출처·확인 상태를 기관 검토용 표 형태로 출력한다. */
@Component
public class DocxExporter implements DocumentExporter {
    public ExportFormat format() {
        return ExportFormat.DOCX;
    }

    public byte[] export(GeneratedDocument document, List<DocumentSection> sections) {
        try (var doc = new XWPFDocument();
                var out = new ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            var run = title.createRun();
            run.setBold(true);
            run.setFontSize(18);
            run.setText(document.getTitle());
            doc.createParagraph()
                    .createRun()
                    .setText(document.getKind().label() + " · 신청 #" + document.getSecurityRequestId() + " · 생성 "
                            + document.getGeneratedAt() + " · 서류 확정은 실제 장비 적용과 다릅니다.");
            for (var section : sections) {
                var heading = doc.createParagraph().createRun();
                heading.setBold(true);
                heading.setFontSize(13);
                heading.setText(section.title());
                var table = doc.createTable();
                table.setWidth("100%");
                table.getRow(0).getCell(0).setText("항목");
                table.getRow(0).addNewTableCell().setText("내용·확인 상태");
                for (var value : section.values()) {
                    var row = table.createRow();
                    row.getCell(0).setText(value.label());
                    var cell = row.getCell(1);
                    for (var line : (value.display() + "\n" + value.status() + " · " + source(value.valueSource()))
                            .split("\\R")) cell.addParagraph().createRun().setText(line);
                }
            }
            if (document.getAiSupplement() != null)
                doc.createParagraph().createRun().setText("AI 보완 · 미확정\n" + document.getAiSupplement());
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("기관 양식 문서 생성 실패", e);
        }
    }

    private static String source(String value) {
        if (value == null) return "기록";
        return switch (value) {
            case "USER_ANSWER" -> "사용자 답변";
            case "AI_INFERRED" -> "AI 추론";
            case "STAFF_INPUT" -> "담당자 입력";
            case "STAFF_CONFIRMED" -> "담당자 확인";
            default -> value;
        };
    }
}
