package kr.securityadvisor.request.repository;

import java.util.Optional;
import kr.securityadvisor.request.domain.FinalDraft;
import org.springframework.data.jpa.repository.JpaRepository;

/** 재생성한 초안도 삭제하지 않고 최신 식별자로 구분한다. */
public interface FinalDraftRepository extends JpaRepository<FinalDraft, Long> {
    Optional<FinalDraft> findFirstBySecurityRequestIdOrderByIdDesc(Long securityRequestId);
}
