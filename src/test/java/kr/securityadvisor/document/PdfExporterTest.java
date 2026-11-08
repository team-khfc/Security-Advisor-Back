package kr.securityadvisor.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.common.schema.ExportFormat;
import kr.securityadvisor.document.domain.DocumentKind;
import kr.securityadvisor.document.domain.GeneratedDocument;
import kr.securityadvisor.document.dto.DocumentSection;
import kr.securityadvisor.document.dto.DocumentValue;
import kr.securityadvisor.document.export.PdfExporter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PDF 출력: 한글이 깨지지 않고, 미확정 표기·AI 보완·바닥글이 들어가며, 긴 값은 줄바꿈·쪽 넘김된다. */
class PdfExporterTest {

    private final PdfExporter exporter = new PdfExporter();

    @Test
    @DisplayName("한글 본문·담당자 확인 필요·합성 데이터 바닥글이 PDF 텍스트로 추출되고 긴 서류는 여러 쪽이 된다")
    void exportsKoreanPdf() throws Exception {
        List<DocumentValue> values = new java.util.ArrayList<>();
        values.add(new DocumentValue(
                "project.name", "사업(서비스)명", "CASE", "합성 민원 포털", "USER_ANSWER", true, false, "합성 민원 포털", "확인 완료"));
        values.add(new DocumentValue(
                "network.source", "출발지", "CASE", null, "AI_INFERRED", false, true, "담당자 확인 필요", "미확정"));
        for (int i = 0; i < 60; i++) {
            values.add(new DocumentValue(
                    "firewall.purpose" + i,
                    "허용 목적 " + i,
                    "REQUEST",
                    "외부 API 연동을 위한 통신 허용 — 합성 시나리오 문장이 길어서 한 줄을 넘기고 자동으로 줄바꿈되는지 확인한다 ".repeat(2),
                    "STAFF_INPUT",
                    true,
                    false,
                    "외부 API 연동을 위한 통신 허용 — 합성 시나리오 문장이 길어서 한 줄을 넘기고 자동으로 줄바꿈되는지 확인한다 ".repeat(2),
                    "확인 완료"));
        }
        GeneratedDocument document = GeneratedDocument.render(
                7L,
                null,
                DocumentKind.DRAFT,
                1,
                "방화벽 정책 허용 신청서",
                "[]",
                "# 방화벽",
                "합성 AI 보완 서술입니다. 😀 이모지는 글꼴에 없어 대체된다.",
                "fake/offline",
                1,
                "applicant",
                Instant.parse("2026-11-08T09:00:00Z"));

        assertThat(exporter.format()).isEqualTo(ExportFormat.PDF);
        byte[] bytes = exporter.export(document, List.of(new DocumentSection("1. 사업 정보", values)));
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");

        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text)
                    .contains("방화벽 정책 허용 신청서")
                    .contains("합성 민원 포털")
                    .contains("담당자 확인 필요")
                    .contains("AI 보완 서술 (미확정")
                    .contains("합성 시연 데이터")
                    .contains("2 쪽")
                    .doesNotContain("😀");
        }
        assertThat(new ByteArrayInputStream(bytes).available()).isLessThan(1_500_000);
    }
}
