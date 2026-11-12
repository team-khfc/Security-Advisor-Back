package kr.securityadvisor.llm.service;

import java.io.IOException;
import java.io.InputStream;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.llm.dto.SourceDocumentResponse;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.UploadedFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 파일 식별자를 추측해도 사업 조회 권한을 통과한 원문만 읽을 수 있다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceDocumentService {
    private final RequestAccessPolicy accessPolicy;
    private final UploadedFileService files;
    private final TextExtractor extractor;

    public SourceDocumentResponse get(MemberPrincipal principal, Long caseId, Long fileId) {
        accessPolicy.viewableCase(principal, caseId);
        UploadedFile file = files.requireFile(caseId, fileId);
        try (InputStream in = files.open(file)) {
            String text = extractor.extract(file.extension(), in);
            return new SourceDocumentResponse(
                    fileId,
                    file.getOriginalName(),
                    file.getUploadedAt(),
                    text,
                    TextExtractor.MAX_CHARS,
                    text.length() >= TextExtractor.MAX_CHARS);
        } catch (IOException e) {
            throw new BadRequestException("원문의 텍스트를 읽을 수 없습니다. 원본 파일을 내려받아 확인해 주세요");
        }
    }
}
