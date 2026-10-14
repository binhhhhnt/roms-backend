package com.roms.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Getter
@Setter
@Builder
public class PageResponse<T> {

    public PageResponse() {
    }

    public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = content != null ? content : Collections.emptyList();
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    @Builder.Default
    private List<T> content = Collections.emptyList();
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <T> PageResponse<T> from(Page<T> springPage) {
        if (springPage == null) {
            return PageResponse.<T>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(0)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        return PageResponse.<T>builder()
                .content(springPage.getContent())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    public static <T, R> PageResponse<R> from(Page<T> springPage, Function<T, R> mapper) {
        if (springPage == null) {
            return PageResponse.<R>builder()
                    .content(Collections.emptyList())
                    .page(0)
                    .size(0)
                    .totalElements(0)
                    .totalPages(0)
                    .build();
        }

        List<R> mappedContent = springPage.getContent().stream()
                .map(mapper)
                .toList();

        return PageResponse.<R>builder()
                .content(mappedContent)
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .totalElements(springPage.getTotalElements())
                .totalPages(springPage.getTotalPages())
                .build();
    }
}
