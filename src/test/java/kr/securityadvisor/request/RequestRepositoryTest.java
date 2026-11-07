package kr.securityadvisor.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import kr.securityadvisor.TestcontainersConfiguration;
import kr.securityadvisor.auth.domain.Member;
import kr.securityadvisor.auth.repository.MemberRepository;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.configuration.domain.Publication;
import kr.securityadvisor.configuration.repository.PublicationRepository;
import kr.securityadvisor.request.domain.Answer;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.Submission;
import kr.securityadvisor.request.repository.AnswerRepository;
import kr.securityadvisor.request.repository.BusinessCaseRepository;
import kr.securityadvisor.request.repository.SecurityRequestRepository;
import kr.securityadvisor.request.repository.SubmissionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

/** 마이그레이션 ↔ 엔티티 매핑, seed 게시본 스키마, 유일 제약(불변식 14)을 실제 PostgreSQL 로 확인한다. */
@Tag("docker") // Testcontainers(PostgreSQL) 필요 — Docker 없는 환경에서는 -PexcludeTags=docker 로 제외
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class RequestRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private BusinessCaseRepository businessCaseRepository;

    @Autowired
    private SecurityRequestRepository securityRequestRepository;

    @Autowired
    private AnswerRepository answerRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Test
    @DisplayName("seed 게시본 v1 이 있고 필드 스키마가 해석된다(공통 9·방화벽 4·보안성검토 3 항목)")
    void seedPublication() {
        Publication publication =
                publicationRepository.findTopByOrderByVersionDesc().orElseThrow();
        FieldSchema schema = new ObjectMapper().readValue(publication.getFieldSchemaJson(), FieldSchema.class);

        assertThat(publication.getVersion()).isEqualTo(1);
        assertThat(schema.activeDocTypes())
                .extracting(definition -> definition.code().code())
                .containsExactly("FIREWALL", "SECURITY_REVIEW");
        assertThat(schema.findDocType(DocTypeCode.of("FIREWALL")).orElseThrow().generatesFirewallRules())
                .isTrue();
        assertThat(schema.caseFields()).hasSize(9);
        assertThat(schema.requestFields(DocTypeCode.of("FIREWALL"))).hasSize(4);
        assertThat(schema.requestFields(DocTypeCode.of("SECURITY_REVIEW"))).hasSize(3);
        assertThat(schema.find("network.source").orElseThrow().isStaffField()).isTrue();
    }

    @Test
    @DisplayName("사업 건·신청·답변·제출본이 저장되고 다시 읽힌다")
    void saveGraph() {
        Long publicationId = publicationRepository
                .findTopByOrderByVersionDesc()
                .orElseThrow()
                .getId();
        memberRepository.saveAndFlush(Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT));
        BusinessCase businessCase = businessCaseRepository.saveAndFlush(
                BusinessCase.create("applicant", "민원 포털", "개요", "정보화부", publicationId));
        SecurityRequest request = securityRequestRepository.saveAndFlush(
                SecurityRequest.create(businessCase.getId(), DocTypeCode.of("FIREWALL")));
        answerRepository.saveAndFlush(
                Answer.userAnswer(AnswerScope.CASE, businessCase.getId(), null, "project.name", "민원 포털"));
        answerRepository.saveAndFlush(
                Answer.staffCheckNeeded(AnswerScope.REQUEST, businessCase.getId(), request.getId(), "firewall.port"));
        submissionRepository.saveAndFlush(Submission.of(
                request.getId(),
                publicationId,
                1,
                "{\"project.name\":\"민원 포털\"}",
                "idem-1",
                "applicant",
                Instant.now()));

        assertThat(businessCaseRepository.findById(businessCase.getId()))
                .get()
                .extracting(BusinessCase::getRevision)
                .isEqualTo(0L);
        assertThat(securityRequestRepository.findByBusinessCaseIdOrderByIdAsc(businessCase.getId()))
                .hasSize(1);
        assertThat(answerRepository.findByBusinessCaseIdAndScopeOrderByFieldKeyAsc(
                        businessCase.getId(), AnswerScope.CASE))
                .hasSize(1);
        assertThat(answerRepository.findBySecurityRequestIdAndFieldKey(request.getId(), "firewall.port"))
                .get()
                .extracting(Answer::getValueSource)
                .isNull();
        assertThat(submissionRepository.countBySecurityRequestId(request.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("한 사업 건 안에 같은 서류 유형의 신청은 하나만 둘 수 있다(불변식 14, DB 유일 제약)")
    void duplicateDocTypeRejected() {
        Long publicationId = publicationRepository
                .findTopByOrderByVersionDesc()
                .orElseThrow()
                .getId();
        memberRepository.saveAndFlush(Member.seed("applicant", "{bcrypt}hash", "김신청", MemberRole.APPLICANT));
        BusinessCase businessCase = businessCaseRepository.saveAndFlush(
                BusinessCase.create("applicant", "민원 포털", null, null, publicationId));
        securityRequestRepository.saveAndFlush(
                SecurityRequest.create(businessCase.getId(), DocTypeCode.of("FIREWALL")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> securityRequestRepository.saveAndFlush(
                        SecurityRequest.create(businessCase.getId(), DocTypeCode.of("FIREWALL"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
