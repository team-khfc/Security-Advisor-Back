package kr.securityadvisor.request.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import kr.securityadvisor.audit.domain.AuditAction;
import kr.securityadvisor.audit.domain.AuditTargetType;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.FileContentCheck;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.dto.UploadedFileResponse;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 업로드 문서(F-02): 신청자 본인, 미제출 신청이 있는 사업 건만. 파일은 선택만으로 전송되지 않는다(명시적 업로드 호출). */
@Service
@Transactional
@RequiredArgsConstructor
public class UploadedFileService {

    private final UploadedFileRepository repository;
    private final SecurityRequestRepository securityRequestRepository;
    private final RequestAccessPolicy accessPolicy;
    private final FileStorage storage;
    private final AuditRecorder auditRecorder;
    private final Clock clock;

    public UploadedFileResponse upload(
            MemberPrincipal principal,
            Long businessCaseId,
            String originalName,
            String contentType,
            long size,
            InputStream content) {
        BusinessCase businessCase = accessPolicy.ownedCase(principal, businessCaseId);
        boolean anyUnsubmitted = securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCaseId).stream()
                .anyMatch(SecurityRequest::isUnsubmitted);
        if (!anyUnsubmitted) {
            throw new DomainRuleViolationException("진행 중(미제출)인 신청이 없어 문서를 올릴 수 없습니다");
        }
        if (content == null) {
            throw new BadRequestException("파일 내용이 없습니다");
        }
        UploadedFile file = UploadedFile.register(
                businessCase.getId(), originalName, contentType, size, principal.username(), clock.instant());
        // 확장자·크기(도메인) 다음에 실제 내용 서명을 검사한다 — 이름만 바꾼 파일 차단(F-02, SECURE_CODING)
        byte[] bytes = readAll(content, size);
        FileContentCheck.requireMatches(file.extension(), bytes);
        storage.save(file.getStoredName(), new ByteArrayInputStream(bytes));
        file = repository.save(file);
        auditRecorder.record(
                principal,
                AuditTargetType.BUSINESS_CASE,
                String.valueOf(businessCaseId),
                businessCase.getRevision(),
                AuditAction.FILE_UPLOADED,
                null,
                "문서 업로드 (파일 " + file.getId() + ", " + file.extension() + ", " + file.getSizeBytes() + "B)");
        return UploadedFileResponse.from(file);
    }

    private static byte[] readAll(InputStream in, long declaredSize) {
        try {
            byte[] bytes = in.readNBytes((int) Math.min(UploadedFile.MAX_SIZE_BYTES + 1, Integer.MAX_VALUE));
            if (bytes.length > UploadedFile.MAX_SIZE_BYTES) {
                throw new DomainRuleViolationException(
                        "파일 크기는 " + (UploadedFile.MAX_SIZE_BYTES / 1024 / 1024) + "MB 이하여야 합니다");
            }
            if (declaredSize > 0 && bytes.length != declaredSize) {
                throw new BadRequestException("파일 크기가 선언된 값과 다릅니다");
            }
            return bytes;
        } catch (IOException e) {
            throw new BadRequestException("파일을 읽을 수 없습니다");
        }
    }

    @Transactional(readOnly = true)
    public List<UploadedFileResponse> list(MemberPrincipal principal, Long businessCaseId) {
        accessPolicy.viewableCase(principal, businessCaseId);
        return repository.findByBusinessCaseIdOrderByIdAsc(businessCaseId).stream()
                .map(UploadedFileResponse::from)
                .toList();
    }

    /** 다른 컨텍스트(llm 분석)가 쓰는 조회. 접근 검사는 호출자가 사업 건 단위로 한다. */
    @Transactional(readOnly = true)
    public UploadedFile requireFile(Long businessCaseId, Long uploadedFileId) {
        UploadedFile file = repository
                .findById(uploadedFileId)
                .orElseThrow(() -> new NotFoundException("업로드 문서를 찾을 수 없습니다: " + uploadedFileId));
        if (!file.getBusinessCaseId().equals(businessCaseId)) {
            throw new NotFoundException("업로드 문서를 찾을 수 없습니다: " + uploadedFileId);
        }
        return file;
    }

    /** 저장된 파일 내용 열기(분석용). */
    public InputStream open(UploadedFile file) {
        try {
            return java.nio.file.Files.newInputStream(storage.resolve(file.getStoredName()));
        } catch (IOException e) {
            throw new NotFoundException("저장된 파일이 없습니다: " + file.getId());
        }
    }
}
