package kr.securityadvisor.document.dto;

import java.util.List;

/** 서류 섹션(공통 정보 / 서류별 정보). */
public record DocumentSection(String title, List<DocumentValue> values) {}
