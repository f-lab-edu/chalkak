package com.chalkak.common.response;

import java.util.List;

import org.springframework.data.domain.Slice;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    boolean hasNext
) {
    public static <T> PageResponse<T> of(Slice<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.hasNext()
        );
    }
}
