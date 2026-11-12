package kr.securityadvisor.llm.service;

import static org.assertj.core.api.Assertions.*;

import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;

class ScannedPdfExtractorTest {
    @Test
    void 지나치게_많은_페이지는_OCR_실행_전에_거부한다() throws Exception {
        try (var document = new PDDocument()) {
            for (int i = 0; i < 101; i++) document.addPage(new PDPage());
            assertThatThrownBy(() -> ScannedPdfExtractor.extract(document))
                    .isInstanceOf(java.io.IOException.class)
                    .hasMessageContaining("100쪽");
        }
    }
}
