package kr.securityadvisor.llm.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import kr.securityadvisor.common.error.NotFoundException;
import kr.securityadvisor.common.schema.AnswerScope;
import kr.securityadvisor.common.schema.FieldDefinition;
import kr.securityadvisor.common.schema.FieldSchema;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.configuration.service.PublicationService;
import kr.securityadvisor.llm.domain.AnalysisStatus;
import kr.securityadvisor.request.domain.BusinessCase;
import kr.securityadvisor.request.domain.SecurityRequest;
import kr.securityadvisor.request.domain.UploadedFile;
import kr.securityadvisor.request.dto.AnswerResponse;
import kr.securityadvisor.request.dto.UploadedFileResponse;
import kr.securityadvisor.request.service.RequestAccessPolicy;
import kr.securityadvisor.request.service.UploadedFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 배정 검사 이후 해당 신청의 사업 자료만 수집한다. 원문과 AI 요약의 출처를 분리한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryContextService {
    private final RequestAccessPolicy access;
    private final PublicationService publications;
    private final UploadedFileService files;
    private final DocumentAnalysisService analyses;
    private final TextExtractor extractor;

    /** 태그와 생성 시점의 근거 설명은 메시지에 함께 보존한다. */
    public record Source(String tag, String label, String text) {}

    /** 원문은 최근 3개 파일의 제한된 발췌이며 읽지 못한 자료는 그 상태를 명시한다. */
    public List<Source> collect(
            MemberPrincipal member, SecurityRequest request, List<AnswerResponse> answers, String question) {
        BusinessCase businessCase = access.viewableCase(member, request.getBusinessCaseId());
        FieldSchema schema = publications.getFieldSchema(businessCase.getPublicationId());
        List<Source> result = new ArrayList<>();
        result.add(new Source(
                "B",
                "사업명·개요 · 사업 수정본 " + businessCase.getRevision(),
                businessCase.getTitle() + "\n" + Objects.toString(businessCase.getSummary(), "")));
        answers.stream()
                .filter(a -> a.value() != null && !a.value().isBlank())
                .limit(80)
                .forEach(a -> {
                    String label = schema.find(a.fieldKey())
                            .map(FieldDefinition::label)
                            .orElse(a.fieldKey());
                    result.add(new Source(
                            "A" + a.answerId(),
                            label + (a.scope() == AnswerScope.CASE ? " · 사업 공통" : " · 신청 항목") + " · 수정본 " + a.revision()
                                    + (a.confirmed() && !a.needsReview() ? " · 확인됨" : " · 미확정/재검토 필요"),
                            a.value().substring(0, Math.min(a.value().length(), 2000)) + " (출처 " + a.valueSource()
                                    + ")"));
                });
        files.list(member, businessCase.getId()).stream()
                .sorted(Comparator.comparing(UploadedFileResponse::uploadedFileId)
                        .reversed())
                .limit(3)
                .forEach(file -> result.add(excerpt(businessCase.getId(), file, question)));
        analyses.listForCase(member, businessCase.getId()).stream()
                .filter(job -> job.status() == AnalysisStatus.NEEDS_REVIEW && job.summary() != null)
                .limit(3)
                .forEach(job -> result.add(new Source(
                        "D" + job.analysisJobId(),
                        "업로드 파일 #" + job.uploadedFileId() + "의 AI 분석 요약 " + job.analysisJobId() + " · 미확정 참고자료",
                        job.summary().substring(0, Math.min(job.summary().length(), 4000)))));
        return List.copyOf(result);
    }

    private Source excerpt(Long caseId, UploadedFileResponse file, String question) {
        String label = "업로드 원문 · 파일 #" + file.uploadedFileId() + " · " + file.originalName();
        try {
            UploadedFile stored = files.requireFile(caseId, file.uploadedFileId());
            String text;
            try (InputStream input = files.open(stored)) {
                text = extractor.extract(stored.extension(), input);
            }
            if (text.isBlank())
                return new Source(
                        "U" + file.uploadedFileId(),
                        label + " · 추출 불가",
                        "문서에서 읽을 수 있는 텍스트가 없습니다. 이 문서의 사실은 추정하지 말고 원문 확인이 필요하다고 안내합니다.");
            int match = -1;
            for (String term : question.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (term.length() < 2) continue;
                int position = text.toLowerCase(Locale.ROOT).indexOf(term);
                if (position >= 0 && (match < 0 || position < match)) match = position;
            }
            int start = Math.max(0, match - 1000);
            int end = Math.min(text.length(), start + 6000);
            return new Source(
                    "U" + file.uploadedFileId(),
                    label + " · 추출 문자 " + (start + 1) + "–" + end + " (앞 20,000자 내 발췌)",
                    text.substring(start, end));
        } catch (IOException | NotFoundException exception) {
            return new Source(
                    "U" + file.uploadedFileId(),
                    label + " · 읽기 실패",
                    "파일의 내용을 읽지 못했습니다. 이 문서의 사실은 추정하지 말고 원문 확인이 필요하다고 안내합니다.");
        }
    }
}
