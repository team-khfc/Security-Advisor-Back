package kr.securityadvisor.auth.repository;

import java.util.List;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.common.security.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 계정 저장소. 기본 키는 username 이다.
 */
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findAllByOrderByUsernameAsc();

    List<Member> findByRoleOrderByUsernameAsc(MemberRole role);

    long countByRoleAndActiveTrue(MemberRole role);
}
