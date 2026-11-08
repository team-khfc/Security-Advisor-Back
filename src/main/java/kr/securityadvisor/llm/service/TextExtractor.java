package kr.securityadvisor.llm.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** 업로드 문서 텍스트 추출(PDF·DOCX·TXT/MD). 길이는 모델 입력 한도에 맞춰 자른다. */
@Component
public class TextExtractor {

    public static final int MAX_CHARS = 20_000;

    public String extract(String extension, InputStream in) throws IOException {
        String text = switch (extension) {
            case "pdf" -> {
                try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
                    yield new PDFTextStripper().getText(doc);
                }
            }
            case "docx" -> {
                try (XWPFDocument doc = new XWPFDocument(in);
                        XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    yield extractor.getText();
                }
            }
            case "txt", "md" -> new String(in.readAllBytes(), StandardCharsets.UTF_8);
            default -> throw new IOException("텍스트를 추출할 수 없는 형식입니다: " + extension);
        };
        String normalized = text == null ? "" : text.replace("\r\n", "\n").strip();
        return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
    }
}
