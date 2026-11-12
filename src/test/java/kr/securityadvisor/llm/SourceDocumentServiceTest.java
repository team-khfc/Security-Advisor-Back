package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.llm.service.SourceDocumentService;
import kr.securityadvisor.llm.service.TextExtractor;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.UploadedFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceDocumentServiceTest {
    private final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    private final UploadedFileService files = mock(UploadedFileService.class);
    private final MemberPrincipal member = new MemberPrincipal("synthetic", "합성", MemberRole.APPLICANT);
    private final SourceDocumentService service = new SourceDocumentService(access, files, new TextExtractor());

    @Test
    @DisplayName("다른 사업의 원문은 파일을 읽기 전에 조회 권한으로 차단한다")
    void checksAccessFirst() {
        when(access.viewableCase(member, 1L)).thenThrow(new NotFoundException("조회 불가"));
        assertThatThrownBy(() -> service.get(member, 1L, 7L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(files);
    }

    @Test
    @DisplayName("추출문은 첨부 원본에서 읽고 AI 요약을 섞지 않는다")
    void returnsOriginalText() {
        byte[] bytes = "합성 원문\n<script>실행하지 않는 문서 내용</script>".getBytes(StandardCharsets.UTF_8);
        var file = UploadedFile.register(1L, "합성.txt", "text/plain", bytes.length, member.username(), Instant.now());
        when(files.requireFile(1L, 7L)).thenReturn(file);
        when(files.open(file)).thenReturn(new ByteArrayInputStream(bytes));
        var result = service.get(member, 1L, 7L);
        assertThat(result.text()).isEqualTo(new String(bytes, StandardCharsets.UTF_8));
        assertThat(result.originalName()).isEqualTo("합성.txt");
        assertThat(result.possiblyTruncated()).isFalse();
    }
}
