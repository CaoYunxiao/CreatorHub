package com.creatorhub.backend.entity;

import java.time.LocalDateTime;

public record WorkDetail(long id, long userId, String title, String intro, String content,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
}
