package kr.securityadvisor.knowledge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.domain.PrivateContext;
import kr.securityadvisor.knowledge.repository.PrivateContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 사실 조회(원칙 3). 서버가 호출자의 역할로 걸러 뽑은 행만 AI 에 넘긴다 — 모델이 검색하지 않는다.
 * 신청자는 항상 빈 결과를 받는다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PrivateContextLookup {

    private final PrivateContextRepository repository;
    private final JsonCodec json;

    /** 텍스트(질문·답변 값)에 이름이나 별칭이 포함된 활성 행 중 호출자 역할에 보이는 것. */
    public List<FactRecord> matchIn(MemberPrincipal principal, List<String> texts) {
        if (principal.hasRole(MemberRole.APPLICANT) || texts == null || texts.isEmpty()) {
            return List.of();
        }
        String haystack = String.join("\n", texts).toLowerCase(Locale.ROOT);
        List<FactRecord> matched = new ArrayList<>();
        for (PrivateContext context : visibleTo(principal)) {
            List<String> names = new ArrayList<>(json.readStrings(context.getAliases()));
            names.add(context.getName());
            boolean hit = names.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .anyMatch(n -> haystack.contains(n.toLowerCase(Locale.ROOT)));
            if (hit) {
                matched.add(toFact(context));
            }
        }
        return matched;
    }

    /** 이름·별칭 부분 일치 검색(담당자 입력 자동완성용). */
    public List<FactRecord> suggest(MemberPrincipal principal, String query) {
        if (principal.hasRole(MemberRole.APPLICANT)) {
            return List.of();
        }
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return visibleTo(principal).stream()
                .filter(context -> q.isEmpty()
                        || context.getName().toLowerCase(Locale.ROOT).contains(q)
                        || json.readStrings(context.getAliases()).stream()
                                .anyMatch(a -> a.toLowerCase(Locale.ROOT).contains(q)))
                .map(this::toFact)
                .toList();
    }

    /** 모델 출력 검증용: 호출자에게 보이는 모든 사실 값(IP·대역 등). */
    public List<String> knownValues(MemberPrincipal principal) {
        return visibleTo(principal).stream()
                .flatMap(context -> json.readStringMap(context.getAttributes()).values().stream())
                .toList();
    }

    private List<PrivateContext> visibleTo(MemberPrincipal principal) {
        String role = principal.role().name();
        return repository.findByActiveTrueOrderByIdAsc().stream()
                .filter(context -> principal.hasRole(MemberRole.ADMIN)
                        || json.readStrings(context.getVisibleRoles()).contains(role))
                .toList();
    }

    FactRecord toFact(PrivateContext context) {
        return new FactRecord(
                context.getId(), context.getKind(), context.getName(), json.readStringMap(context.getAttributes()));
    }
}
