package kr.securityadvisor.configuration.dto;

import java.time.Instant;
import kr.securityadvisor.common.schema.FieldSchema;

/**
 * 게시본 응답 — 프론트가 인터뷰 화면을 그리는 데 쓰는 필드 스키마.
 *
 * @param publicationId 게시본 ID(사업 건 생성 시 고정됨)
 * @param version 게시 버전
 * @param publishedAt 게시 시각
 * @param fieldSchema 질문 항목 목록
 */
public record PublicationResponse(Long publicationId, int version, Instant publishedAt, FieldSchema fieldSchema) {}
