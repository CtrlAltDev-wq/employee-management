package com.wangqi.employee.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope. Clients get everything they need to page through
 * a result set without parsing Spring's internal {@code Page} JSON, which is
 * not a contractual API shape.
 *
 * @param content       the page items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching rows across all pages
 * @param totalPages    number of pages available
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 * @author wangqi
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
