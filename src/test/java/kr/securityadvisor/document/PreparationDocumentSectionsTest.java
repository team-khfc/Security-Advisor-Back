package kr.securityadvisor.document;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import kr.securityadvisor.document.service.PreparationDocumentSections;
import kr.securityadvisor.request.dto.PreparationReviewResponse;
import org.junit.jupiter.api.Test;

class PreparationDocumentSectionsTest {
    @Test
    void 긴_검토사유와_파일목록이_PDF_여러쪽에_끝까지_남는다() throws Exception {
        var data = new PreparationReviewResponse(
                1,
                1,
                "hash",
                Instant.EPOCH,
                Map.of(),
                List.of(new PreparationReviewResponse.Item(
                        "plan",
                        "사업계획서",
                        "SECURITY",
                        "APPLICABLE",
                        "NEEDS_SUPPLEMENT",
                        false,
                        "합성 검토 사유입니다.\n".repeat(100) + "보완사유끝",
                        "합성 담당자",
                        Instant.EPOCH,
                        List.of(new PreparationReviewResponse.File(7L, "합성.txt", Instant.EPOCH)))));
        var document = kr.securityadvisor.document.domain.GeneratedDocument.render(
                1L,
                1L,
                kr.securityadvisor.document.domain.DocumentKind.SUBMITTED,
                1,
                "합성 제출본",
                "[]",
                "합성 제출본 본문",
                null,
                null,
                0,
                "owner",
                Instant.EPOCH);
        byte[] bytes = new kr.securityadvisor.document.export.PdfExporter()
                .export(document, PreparationDocumentSections.from(data));
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
                    .contains("보완사유끝", "합성.txt", "자료 현황 기록", "보완 필요");
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void 과거_제출본의_없는_기록을_추정하지_않는다() {
        assertThat(PreparationDocumentSections.from(null)
                        .getFirst()
                        .values()
                        .getFirst()
                        .display())
                .contains("기록이 없습니다");
    }

    @Test
    void 문서에_보완사유_파일명_담당자와_재검토를_표시한다() {
        var record = new PreparationReviewResponse(
                1,
                2,
                "hash",
                Instant.EPOCH,
                Map.of(),
                List.of(new PreparationReviewResponse.Item(
                        "plan",
                        "사업계획서",
                        "SECURITY",
                        "APPLICABLE",
                        "IN_REVIEW",
                        true,
                        "합성 범위 보완",
                        "합성 담당자",
                        Instant.EPOCH,
                        List.of(new PreparationReviewResponse.File(7L, "합성.txt", Instant.EPOCH)))));
        String text = PreparationDocumentSections.from(record)
                .getFirst()
                .values()
                .get(1)
                .display();
        assertThat(text).contains("재검토", "합성.txt (#7)", "합성 담당자", "합성 범위 보완", "이전 조건");
    }
}
