package com.creatorhub.backend.dto;

import java.time.LocalDateTime;

/** 对外接口契约，与数据库查询对象分开演进。 */
public record WorkSummaryResponse(long id, long authorId, String title, String intro,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
}
