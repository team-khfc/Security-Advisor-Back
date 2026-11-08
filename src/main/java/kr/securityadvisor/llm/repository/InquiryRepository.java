package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.Inquiry;
import org.springframework.data.jpa.repository.JpaRepository;

/** 질의 대화 저장소. */
public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findBySecurityRequestIdAndStaffUsernameOrderByIdDesc(Long securityRequestId, String staffUsername);
}
