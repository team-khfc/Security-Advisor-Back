package kr.securityadvisor.request.dto;

import java.util.List;

/** 검색 조건 전체의 건수와 현재 페이지. 건수는 현재 페이지 길이와 구분한다. */
public record RequestListResponse(
        List<SecurityRequestListItem> content, int page, int size, long totalElements, Summary summary) {
    /** 같은 권한·검색 조건 안에서 업무함별 건수를 계산한다. */
    public record Summary(long all, long mine, long staff, long done) {}
}
