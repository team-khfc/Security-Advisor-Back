package kr.securityadvisor.common.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답(AGENTS.md 5장 표현 형식): content·page(0부터)·size·totalElements.
 *
 * @param <T> 항목 DTO
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
