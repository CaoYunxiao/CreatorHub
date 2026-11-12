package com.creatorhub.backend.dto;

import java.time.LocalDateTime;

public record WorkDetailResponse(long id, long authorId, String title, String intro, String content,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
}
