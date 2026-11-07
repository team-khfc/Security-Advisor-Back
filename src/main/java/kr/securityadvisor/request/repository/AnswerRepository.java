package kr.securityadvisor.request.repository;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.request.domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 답변 저장소. 답변은 사업 건·신청에 속하지만 양쪽 컬렉션으로 매핑하지 않고 ID 로 묶는다(두 범위가 한 테이블을 쓰기 때문).
 * 답변 규칙은 {@link Answer} 의 도메인 메서드가 지키고, 이 저장소는 애플리케이션 서비스만 쓴다.
 */
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findByBusinessCaseIdAndScopeOrderByFieldKeyAsc(Long businessCaseId, AnswerScope scope);

    List<Answer> findBySecurityRequestIdOrderByFieldKeyAsc(Long securityRequestId);

    Optional<Answer> findByBusinessCaseIdAndScopeAndFieldKey(Long businessCaseId, AnswerScope scope, String fieldKey);

    Optional<Answer> findBySecurityRequestIdAndFieldKey(Long securityRequestId, String fieldKey);
}
