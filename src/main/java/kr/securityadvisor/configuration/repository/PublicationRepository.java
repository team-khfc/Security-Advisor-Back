package kr.securityadvisor.configuration.repository;

import java.util.Optional;
import kr.securityadvisor.configuration.domain.Publication;
import org.springframework.data.jpa.repository.JpaRepository;

/** 게시본 저장소. 현재 게시본은 version 이 가장 큰 것이다. */
public interface PublicationRepository extends JpaRepository<Publication, Long> {

    Optional<Publication> findTopByOrderByVersionDesc();
}
