package kr.securityadvisor.document.repository;

import java.util.List;
import kr.securityadvisor.document.domain.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;

/** 렌더링된 서류 저장소. 추가·조회만. */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    List<GeneratedDocument> findBySecurityRequestIdOrderByVersionDesc(Long securityRequestId);

    int countBySecurityRequestId(Long securityRequestId);
}
