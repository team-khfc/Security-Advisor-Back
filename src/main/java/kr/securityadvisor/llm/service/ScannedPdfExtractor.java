package kr.securityadvisor.llm.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

/** 스캔 페이지는 외부 AI로 보내지 않고 설치된 OCR로 인식한다. 원본 파일은 변경하지 않는다. */
final class ScannedPdfExtractor {
    private static final java.util.concurrent.Semaphore CAPACITY = new java.util.concurrent.Semaphore(2);

    private ScannedPdfExtractor() {}

    static String extract(PDDocument document) throws IOException {
        if (!CAPACITY.tryAcquire()) throw new IOException("다른 문서를 인식 중입니다. 잠시 후 다시 시도해 주세요.");
        try {
            return extractPages(document);
        } finally {
            CAPACITY.release();
        }
    }

    private static String extractPages(PDDocument document) throws IOException {
        if (document.getNumberOfPages() > 100) throw new IOException("PDF는 100쪽 이하로 나누어 첨부해 주세요.");
        var out = new StringBuilder();
        var renderer = new PDFRenderer(document);
        int scanned = 0;
        boolean hasContent = false;
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            var stripper = new PDFTextStripper();
            stripper.setStartPage(page + 1);
            stripper.setEndPage(page + 1);
            String text = stripper.getText(document).strip();
            if (text.isBlank()) {
                if (++scanned > 8) throw new IOException("스캔 PDF는 8쪽 이하로 나누어 첨부해 주세요.");
                var box = document.getPage(page).getCropBox();
                if (box.getWidth() <= 0 || box.getHeight() <= 0 || box.getWidth() * box.getHeight() > 2_000_000)
                    throw new IOException("PDF 페이지 크기가 허용 범위를 초과했습니다.");
                Path folder = Files.createTempDirectory("securityadvisor-ocr-");
                Path image = folder.resolve("page.png"), output = folder.resolve("recognized.txt");
                Process process = null;
                try {
                    ImageIO.write(renderer.renderImageWithDPI(page, 144), "png", image.toFile());
                    process = new ProcessBuilder(
                                    "tesseract",
                                    image.toString(),
                                    folder.resolve("recognized").toString(),
                                    "-l",
                                    "kor+eng",
                                    "--psm",
                                    "3")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
                    if (!process.waitFor(12, TimeUnit.SECONDS))
                        throw new IOException("스캔 인식 시간이 초과됐습니다. 문서를 나누어 다시 시도해 주세요.");
                    if (process.exitValue() != 0 || !Files.exists(output)) throw new IOException("스캔 인식에 실패했습니다.");
                    if (Files.size(output) > 1_000_000) throw new IOException("스캔 인식 결과가 너무 큽니다.");
                    String recognized = Files.readString(output).strip();
                    text = recognized.isBlank() ? "" : "[OCR 인식문 · 원본 대조 필요]\n" + recognized;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("스캔 인식이 중단됐습니다.", e);
                } finally {
                    if (process != null && process.isAlive()) {
                        process.destroyForcibly();
                        try {
                            process.waitFor(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    Files.deleteIfExists(image);
                    Files.deleteIfExists(output);
                    Files.deleteIfExists(folder);
                }
            }
            hasContent |= !text.isBlank();
            out.append("[원본 ").append(page + 1).append("쪽]\n").append(text).append('\n');
            if (out.length() > TextExtractor.MAX_CHARS) break;
        }
        return hasContent ? out.toString() : "";
    }
}
