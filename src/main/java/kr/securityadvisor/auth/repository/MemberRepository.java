package kr.securityadvisor.auth.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.common.security.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 계정 저장소. 기본 키는 username 이다.
 */
public interface MemberRepository extends JpaRepository<Member, String> {

    List<Member> findAllByOrderByUsernameAsc();

    List<Member> findByRoleOrderByUsernameAsc(MemberRole role);

    long countByRoleAndActiveTrue(MemberRole role);

    /** 계정 쓰기를 직렬화해 로그인 기록이 동시에 변경된 역할·세션 세대를 덮어쓰지 않게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.username = :username")
    Optional<Member> findForUpdate(@Param("username") String username);
}
