package kr.securityadvisor.knowledge.service;

import java.util.List;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.knowledge.dto.KnowledgeSnippetCommand;
import kr.securityadvisor.knowledge.dto.PrivateContextCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 관리 입력은 참조 무결성과 크기를 검사한다. 공개 문서 URL을 서버에서 가져오지 않는다. */
@Component
@RequiredArgsConstructor
public class KnowledgeValidator {
    private final PublicationService publications;

    public void snippet(KnowledgeSnippetCommand command) {
        var schema = publications.getFieldSchema(publications.currentPublicationId());
        if (command.fieldKeys() == null
                || command.fieldKeys().isEmpty()
                || command.fieldKeys().size() > 200
                || command.fieldKeys().stream()
                        .anyMatch(key -> key == null || schema.find(key).isEmpty()))
            throw new DomainRuleViolationException("적용 질문은 현재 기관 설정 게시본의 질문 중 하나 이상 선택해 주세요");
        if (command.docTypes() != null
                && (command.docTypes().size() > 20
                        || command.docTypes().stream()
                                .anyMatch(code -> schema.docTypes().stream()
                                        .noneMatch(d -> d.code().code().equals(code)))))
            throw new DomainRuleViolationException("적용 서류 유형이 올바르지 않습니다");
    }

    public void context(PrivateContextCommand command) {
        if (command.visibleRoles() == null
                || command.visibleRoles().isEmpty()
                || command.visibleRoles().stream()
                        .anyMatch(role -> role == null
                                || !Set.of("INFRA", "SECURITY", "ADMIN").contains(role)))
            throw new DomainRuleViolationException("열람 역할은 ICT인프라·정보보안·기관 관리자만 지정할 수 있습니다");
        List<String> aliases = command.aliases() == null ? List.of() : command.aliases();
        if (aliases.size() > 20 || aliases.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 100))
            throw new DomainRuleViolationException("별칭은 100자 이하의 값으로 최대 20개까지 입력해 주세요");
        if (command.attributes() == null
                || command.attributes().isEmpty()
                || command.attributes().size() > 50
                || command.attributes().entrySet().stream()
                        .anyMatch(e -> e.getKey() == null
                                || e.getKey().isBlank()
                                || e.getKey().length() > 100
                                || e.getValue() == null
                                || e.getValue().isBlank()
                                || e.getValue().length() > 2000))
            throw new DomainRuleViolationException("정보 속성은 1~50개이며 이름은 100자, 값은 2000자 이하로 입력해 주세요");
    }
}
