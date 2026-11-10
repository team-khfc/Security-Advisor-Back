package kr.securityadvisor.knowledge.repository;

import java.util.List;
import kr.securityadvisor.knowledge.domain.PrivateContext;
import org.springframework.data.jpa.repository.JpaRepository;

/** 비공개 컨텍스트 저장소. 매칭은 서비스가 이름·별칭으로 한다(행 수가 적다). */
public interface PrivateContextRepository extends JpaRepository<PrivateContext, Long> {

    List<PrivateContext> findByActiveTrueOrderByIdAsc();

    List<PrivateContext> findAllByOrderByIdAsc();

    @org.springframework.data.jpa.repository.Query(
            "select c from PrivateContext c where :q = '' or lower(c.name) like lower(concat('%', :q, '%')) order by c.id")
    org.springframework.data.domain.Page<PrivateContext> searchDraft(
            @org.springframework.data.repository.query.Param("q") String query,
            org.springframework.data.domain.Pageable pageable);
}
