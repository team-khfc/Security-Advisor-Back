package kr.securityadvisor.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import kr.securityadvisor.common.schema.DocType;

/**
 * 사업 건 생성 요청(F-01). 서류 유형을 하나 이상 고르면 유형별 신청이 함께 만들어진다.
 *
 * @param title 사업(서비스)명
 * @param summary 사업 개요(직접 입력 또는 AI 요약 결과를 확인한 값)
 * @param department 주관 부서
 * @param docTypes 필요한 서류 유형(1개 이상, 중복 없음)
 */
public record CreateBusinessCaseRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String summary,
        @Size(max = 100) String department,
        @NotEmpty Set<DocType> docTypes) {}
