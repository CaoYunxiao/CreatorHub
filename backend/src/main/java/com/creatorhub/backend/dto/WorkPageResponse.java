package com.creatorhub.backend.dto;

import java.util.List;

/** 页码从 1 开始；当前不提供总条数。 */
public record WorkPageResponse(int page, int size, List<WorkSummaryResponse> items) {
    public WorkPageResponse {
        items = List.copyOf(items);
    }
}
