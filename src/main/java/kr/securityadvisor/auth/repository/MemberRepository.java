package kr.securityadvisor.auth.repository;

import kr.securityadvisor.auth.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 계정 저장소. 기본 키는 username 이다.
 */
public interface MemberRepository extends JpaRepository<Member, String> {}
