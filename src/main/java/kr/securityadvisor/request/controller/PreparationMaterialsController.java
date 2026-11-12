package kr.securityadvisor.request.controller;

import jakarta.validation.Valid;
import kr.securityadvisor.common.security.*;
import kr.securityadvisor.request.dto.*;
import kr.securityadvisor.request.service.PreparationMaterialsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 신청 권한에 따른 준비자료 조회·연결·검토. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/security-requests/{id}/preparation-materials")
public class PreparationMaterialsController {
    private final PreparationMaterialsService service;
    private final kr.securityadvisor.request.service.UploadedFileService files;
    /** 선택된 파일은 별도의 업로드 요청으로만 저장한다. */
    @PostMapping(value = "/files", consumes = "multipart/form-data")
    public UploadedFileResponse uploadPreparationFile(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        return files.uploadPreparation(
                actor, id, file.getOriginalFilename(), file.getContentType(), file.getSize(), file.getInputStream());
    }

    @GetMapping
    public PreparationMaterialsResponse getPreparationMaterials(
            @CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        return service.get(actor, id);
    }

    /** 최종 검토용 현재 자료 묶음. 제출된 자료 묶음은 제출본 조회 API에서 읽는다. */
    @GetMapping("/final-review")
    public PreparationReviewResponse getPreparationFinalReview(
            @CurrentMember MemberPrincipal actor, @PathVariable Long id) {
        return service.finalReview(actor, id);
    }

    @PutMapping
    public PreparationMaterialsResponse updatePreparationMaterials(
            @CurrentMember MemberPrincipal actor,
            @PathVariable Long id,
            @Valid @RequestBody PreparationMaterialsCommand command) {
        return service.update(actor, id, command);
    }
}
