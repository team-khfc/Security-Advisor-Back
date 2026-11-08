package kr.securityadvisor.llm.repository;

import java.util.List;
import kr.securityadvisor.llm.domain.InquiryMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** 질의 메시지 저장소. 추가·조회만. */
public interface InquiryMessageRepository extends JpaRepository<InquiryMessage, Long> {

    List<InquiryMessage> findByInquiryIdOrderByIdAsc(Long inquiryId);

    List<InquiryMessage> findTop10ByInquiryIdOrderByIdDesc(Long inquiryId);
}
