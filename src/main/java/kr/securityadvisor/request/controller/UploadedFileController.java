package kr.securityadvisor.request.controller;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import kr.securityadvisor.common.error.BadRequestException;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.dto.UploadedFileResponse;
import kr.securityadvisor.request.service.UploadedFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 업로드 문서 API(F-02). multipart 필드 이름은 file. */
@RestController
@RequestMapping("/api/business-cases/{id}/uploaded-files")
@RequiredArgsConstructor
public class UploadedFileController {

    private final UploadedFileService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadedFileResponse> upload(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @RequestPart("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("빈 파일입니다");
        }
        UploadedFileResponse response = service.upload(
                member, id, file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream());
        return ResponseEntity.created(
                        URI.create("/api/business-cases/" + id + "/uploaded-files/" + response.uploadedFileId()))
                .body(response);
    }

    @GetMapping
    public List<UploadedFileResponse> list(@CurrentMember MemberPrincipal member, @PathVariable Long id) {
        return service.list(member, id);
    }
}
