package kr.securityadvisor.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.securityadvisor.audit.service.AuditRecorder;
import kr.securityadvisor.common.error.ForbiddenException;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.error.ServiceUnavailableException;
import kr.securityadvisor.common.json.JsonCodec;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.DocTypeCode;
import kr.securityadvisor.common.schema.ReviewField;
import kr.securityadvisor.common.schema.ValueSource;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.common.security.MemberRole;
import kr.securityadvisor.knowledge.service.KnowledgeSearch;
import kr.securityadvisor.knowledge.service.PrivateContextLookup;
import kr.securityadvisor.llm.domain.Inquiry;
import kr.securityadvisor.llm.domain.InquiryMessage;
import kr.securityadvisor.llm.domain.LlmPurpose;
import kr.securityadvisor.llm.dto.InquiryMessageCommand;
import kr.securityadvisor.llm.dto.InquiryMessageResponse;
import kr.securityadvisor.llm.dto.InquiryTurnResult;
import kr.securityadvisor.llm.repository.InquiryMessageRepository;
import kr.securityadvisor.llm.repository.InquiryRepository;
import kr.securityadvisor.llm.service.InquiryContextService;
import kr.securityadvisor.llm.service.InquiryService;
import kr.securityadvisor.llm.service.InternalInfoGuard;
import kr.securityadvisor.llm.service.LlmGateway;
import kr.securityadvisor.llm.service.LlmPrompt;
import kr.securityadvisor.llm.service.LlmResult;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.service.AnswerService;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.review.service.ReviewerAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class InquiryServiceTest {
    private final InquiryRepository inquiries = mock(InquiryRepository.class);
    private final InquiryMessageRepository messages = mock(InquiryMessageRepository.class);
    private final RequestAccessPolicy access = mock(RequestAccessPolicy.class);
    private final ReviewerAccessPolicy reviewers = mock(ReviewerAccessPolicy.class);
    private final AnswerService answers = mock(AnswerService.class);
    private final InquiryContextService context = mock(InquiryContextService.class);
    private final KnowledgeSearch knowledge = mock(KnowledgeSearch.class);
    private final PrivateContextLookup facts = mock(PrivateContextLookup.class);
    private final LlmGateway gateway = mock(LlmGateway.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final InquiryService service = new InquiryService(
            inquiries,
            messages,
            access,
            reviewers,
            answers,
            context,
            knowledge,
            facts,
            gateway,
            new InternalInfoGuard(),
            mock(AuditRecorder.class),
            new JsonCodec(mapper),
            mapper);
    private final MemberPrincipal member = new MemberPrincipal("infra", "담당자", MemberRole.INFRA);
    private final SecurityRequest request = SecurityRequest.create(4L, DocTypeCode.of("FIREWALL"));

    @BeforeEach
    void setup() {
        Inquiry inquiry = Inquiry.open(2L, "infra", ReviewField.INFRA, "합성 질문");
        ReflectionTestUtils.setField(inquiry, "id", 3L);
        ReflectionTestUtils.setField(request, "id", 2L);
        when(inquiries.findById(3L)).thenReturn(Optional.of(inquiry));
        when(access.viewableRequest(member, 2L)).thenReturn(request);
        when(context.collect(eq(member), eq(request), anyList(), anyString()))
                .thenReturn(List.of(
                        new InquiryContextService.Source("A1", "목적지 · 수정본 5 · 확인됨", "10.2.3.4"),
                        new InquiryContextService.Source("U8", "합성자료.txt · 추출 문자 1–100", "원문"),
                        new InquiryContextService.Source("D9", "AI 요약 · 미확정 참고자료", "요약")));
        when(messages.save(any())).thenAnswer(call -> {
            InquiryMessage message = call.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 20L);
            return message;
        });
    }

    @Test
    void savesEvidenceLabelsAndReadsSnapshotsWithoutLookingUpChangedSources() {
        when(gateway.complete(eq(LlmPurpose.INQUIRY), any(), any()))
                .thenReturn(new LlmResult("{\"answer\":\"검토할 내용\",\"evidence\":[\"U8\",\"D9\",\"U8\"]}", "test"));
        InquiryTurnResult result = service.ask(member, 3L, new InquiryMessageCommand("자료를 확인해 줘"));
        assertThat(result.answer().evidence())
                .extracting(InquiryMessageResponse.Evidence::label)
                .containsExactly("합성자료.txt · 추출 문자 1–100", "AI 요약 · 미확정 참고자료");
        ArgumentCaptor<InquiryMessage> saved = ArgumentCaptor.forClass(InquiryMessage.class);
        verify(messages, times(2)).save(saved.capture());
        when(messages.findByInquiryIdOrderByIdAsc(3L)).thenReturn(saved.getAllValues());
        assertThat(service.messages(member, 3L).get(1).evidence())
                .isEqualTo(result.answer().evidence());
        ArgumentCaptor<LlmPrompt> prompt = ArgumentCaptor.forClass(LlmPrompt.class);
        verify(gateway).complete(eq(LlmPurpose.INQUIRY), prompt.capture(), any());
        assertThat(prompt.getValue().system()).contains("[U8]", "[D9]", "미확정");
    }

    @Test
    void allowsStaffConfirmedValuesButRedactsUnknownIpAndFlagsUnknownReferences() {
        when(answers.allAnswers(request))
                .thenReturn(List.of(new AnswerResponse(
                        1L,
                        "destination",
                        AnswerScope.REQUEST,
                        "10.2.3.4",
                        ValueSource.STAFF_CONFIRMED,
                        null,
                        true,
                        "infra",
                        null,
                        false,
                        5L)));
        when(gateway.complete(eq(LlmPurpose.INQUIRY), any(), any()))
                .thenReturn(new LlmResult(
                        "{\"answer\":\"10.2.3.4 확인, 192.168.99.88 추측\",\"evidence\":[\"A1\",\"U999\"]}", "test"));
        var result = service.ask(member, 3L, new InquiryMessageCommand("IP 확인"));
        assertThat(result.answer().text()).contains("10.2.3.4", "담당자 확인 필요").doesNotContain("192.168.99.88");
        assertThat(result.answer().flagged()).isTrue();
        assertThat(result.answer().evidence())
                .extracting(InquiryMessageResponse.Evidence::tag)
                .containsExactly("A1");
    }

    @Test
    void invalidModelResponseDoesNotPersistEitherMessage() {
        when(gateway.complete(eq(LlmPurpose.INQUIRY), any(), any())).thenReturn(new LlmResult("잘못된 응답", "test"));
        assertThatThrownBy(() -> service.ask(member, 3L, new InquiryMessageCommand("질문")))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void readsLegacyTagsWithoutInventingLabels() {
        when(messages.findByInquiryIdOrderByIdAsc(3L))
                .thenReturn(List.of(InquiryMessage.assistant(3L, "이전 답변", "[\"P7\"]", "old", false)));
        assertThat(service.messages(member, 3L).getFirst().evidence())
                .containsExactly(new InquiryMessageResponse.Evidence("P7", null));
    }

    @Test
    void forbidsHistoryAndListsAfterAssignmentRevocation() {
        doThrow(new ForbiddenException("배정 해제")).when(reviewers).requireAssignedReviewer(member, 2L, ReviewField.INFRA);
        assertThatThrownBy(() -> service.messages(member, 3L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.listMine(member, 2L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.ask(member, 3L, new InquiryMessageCommand("질문")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void hidesPreviousRoleTitlesFromList() {
        when(inquiries.findBySecurityRequestIdAndStaffUsernameOrderByIdDesc(2L, "infra"))
                .thenReturn(List.of(Inquiry.open(2L, "infra", ReviewField.SECURITY, "이전 역할의 민감한 제목")));
        assertThat(service.listMine(member, 2L)).isEmpty();
    }

    @Test
    void hidesOtherOwnersAndBlocksRoleChanges() {
        assertThatThrownBy(() -> service.messages(new MemberPrincipal("other", "다른 담당자", MemberRole.INFRA), 3L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.messages(new MemberPrincipal("infra", "역할 변경", MemberRole.SECURITY), 3L))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(gateway);
    }
}
