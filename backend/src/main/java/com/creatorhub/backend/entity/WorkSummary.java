package com.creatorhub.backend.entity;

import java.time.LocalDateTime;

/** works 表的列表查询投影，不加载较大的正文 content。 */
public record WorkSummary(long id, long userId, String title, String intro,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
}
