package com.wangqi.employee.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "PageResponse", description = "Stable pagination envelope, independent of Spring's internal Page serialisation")
public record PageResponse<T>(

        @Schema(description = "Items on the current page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Page size used for this response", example = "20")
        int size,

        @Schema(description = "Total matching rows across all pages", example = "42")
        long totalElements,

        @Schema(description = "Total number of pages", example = "3")
        int totalPages,

        @Schema(description = "Whether this is the first page", example = "true")
        boolean first,

        @Schema(description = "Whether this is the last page", example = "false")
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
