package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import kr.securityadvisor.llm.dto.AnalysisJobResponse;
import kr.securityadvisor.llm.service.DocumentAnalysisService;
import kr.securityadvisor.llm.service.InquiryContextService;
import kr.securityadvisor.llm.service.TextExtractor;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.dto.UploadedFileResponse;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.UploadedFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InquiryContextServiceTest {
    private final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    private final PublicationService publications = mock(PublicationService.class);
    private final UploadedFileService files = mock(UploadedFileService.class);
    private final DocumentAnalysisService analyses = mock(DocumentAnalysisService.class);
    private final InquiryContextService service =
            new InquiryContextService(access, publications, files, analyses, new TextExtractor());
    private final MemberPrincipal member = new MemberPrincipal("security", "정보보안", MemberRole.SECURITY);
    private final SecurityRequest request = mock(SecurityRequest.class);

    @BeforeEach
    void setup() {
        BusinessCase businessCase = mock(BusinessCase.class);
        when(request.getBusinessCaseId()).thenReturn(4L);
        when(access.viewableCase(member, 4L)).thenReturn(businessCase);
        when(businessCase.getId()).thenReturn(4L);
        when(businessCase.getPublicationId()).thenReturn(1L);
        when(businessCase.getTitle()).thenReturn("합성 사업");
        when(publications.getFieldSchema(1L)).thenReturn(new FieldSchema(null, null, null));
    }

    @Test
    void limitsLatestFilesAndQuotesOnlyThisCaseWithExtractedLocation() throws Exception {
        when(files.list(member, 4L)).thenReturn(List.of(file(1L), file(2L), file(3L), file(4L)));
        for (long id = 2; id <= 4; id++) {
            UploadedFile stored = mock(UploadedFile.class);
            when(files.requireFile(4L, id)).thenReturn(stored);
            when(stored.extension()).thenReturn("txt");
            String body = "가".repeat(9000) + "접근제어: 사전승인 필수" + "나".repeat(12000);
            when(files.open(stored)).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }
        var sources = service.collect(member, request, List.of(), "접근제어");
        assertThat(sources).extracting(InquiryContextService.Source::tag).containsExactly("B", "U4", "U3", "U2");
        assertThat(sources.get(1).text()).contains("접근제어: 사전승인 필수").hasSize(6000);
        assertThat(sources.get(1).label()).contains("추출 문자 8001–14000", "합성4.txt");
        verify(files, never()).requireFile(anyLong(), org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    void statesUnreadableFilesAndKeepsOnlyCompletedAiSummariesUnconfirmed() throws Exception {
        when(files.list(member, 4L)).thenReturn(List.of(file(5L)));
        UploadedFile stored = mock(UploadedFile.class);
        when(files.requireFile(4L, 5L)).thenReturn(stored);
        when(stored.extension()).thenReturn("pdf");
        when(files.open(stored)).thenReturn(InputStream.nullInputStream());
        when(analyses.listForCase(member, 4L))
                .thenReturn(List.of(new AnalysisJobResponse(
                        7L,
                        4L,
                        5L,
                        AnalysisStatus.NEEDS_REVIEW,
                        "",
                        "합성 AI 요약",
                        "model",
                        null,
                        "applicant",
                        null,
                        null)));
        var sources = service.collect(member, request, List.of(), "확인");
        assertThat(sources.get(1).label()).contains("읽기 실패");
        assertThat(sources.get(1).text()).contains("사실은 추정하지 말고");
        assertThat(sources.get(2).tag()).isEqualTo("D7");
        assertThat(sources.get(2).label()).contains("미확정 참고자료");
    }

    private UploadedFileResponse file(Long id) {
        return new UploadedFileResponse(id, 4L, "합성" + id + ".txt", "text/plain", 10L, "applicant", Instant.EPOCH);
    }
}
