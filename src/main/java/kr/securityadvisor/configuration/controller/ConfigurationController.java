package kr.securityadvisor.configuration.controller;

import jakarta.validation.Valid;
import java.net.URI;
import kr.securityadvisor.common.security.CurrentMember;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.dto.ConfigurationDraftCommand;
import kr.securityadvisor.configuration.dto.ConfigurationResponse;
import kr.securityadvisor.configuration.dto.PublicationResponse;
import kr.securityadvisor.configuration.dto.PublishCommand;
import kr.securityadvisor.configuration.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기관 설정 편집·게시 API(F-08, 관리자). 게시는 설정의 하위 자원 생성(POST …/publications). */
@RestController
@RequestMapping("/api/configurations")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService service;

    @GetMapping("/current")
    public ConfigurationResponse current(@CurrentMember MemberPrincipal member) {
        return service.current(member);
    }

    @PutMapping("/{id}")
    public ConfigurationResponse saveDraft(
            @CurrentMember MemberPrincipal member,
            @PathVariable Long id,
            @Valid @RequestBody ConfigurationDraftCommand command) {
        return service.saveDraft(member, id, command);
    }

    @PostMapping("/{id}/publications")
    public ResponseEntity<PublicationResponse> publish(
            @CurrentMember MemberPrincipal member, @PathVariable Long id, @Valid @RequestBody PublishCommand command) {
        PublicationResponse response = service.publish(member, id, command);
        return ResponseEntity.created(URI.create("/api/publications/" + response.publicationId()))
                .body(response);
    }
}
