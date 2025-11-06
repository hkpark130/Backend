package kr.co.direa.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;
    private final Map<String, Object> metadata;

    private PageResponse(List<T> content,
                         int page,
                         int size,
                         long totalElements,
                         int totalPages,
                         boolean first,
                         boolean last,
                         Map<String, Object> metadata) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.first = first;
        this.last = last;
        this.metadata = metadata;
    }

    public static <T> PageResponse<T> of(List<T> content,
                                         int page,
                                         int size,
                                         long totalElements,
                                         int totalPages,
                                         Map<String, Object> metadata) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        long safeTotalElements = Math.max(totalElements, 0);
        int safeTotalPages = Math.max(totalPages, 1);
        boolean isFirst = safePage <= 1;
        boolean isLast = safePage >= safeTotalPages;
        List<T> immutableContent = content == null ? List.of() : List.copyOf(content);
        Map<String, Object> immutableMetadata =
                metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
        return new PageResponse<>(immutableContent,
                safePage,
                safeSize,
                safeTotalElements,
                safeTotalPages,
                isFirst,
                isLast,
                immutableMetadata);
    }
}
