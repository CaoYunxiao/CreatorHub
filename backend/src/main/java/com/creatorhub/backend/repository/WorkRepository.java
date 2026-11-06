package com.creatorhub.backend.repository;

import java.util.List;

import com.creatorhub.backend.entity.WorkSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRepository {
    private final JdbcTemplate jdbcTemplate;

    public WorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WorkSummary> findPage(int limit, long offset) {
        // 排序字段固定，分页参数绑定，不把请求参数拼入 SQL。
        return jdbcTemplate.query("""
                SELECT id, user_id, title, intro, created_at, updated_at
                FROM works
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, (result, rowNumber) -> new WorkSummary(
                result.getLong("id"), result.getLong("user_id"),
                result.getString("title"), result.getString("intro"),
                result.getObject("created_at", java.time.LocalDateTime.class),
                result.getObject("updated_at", java.time.LocalDateTime.class)), limit, offset);
    }
}
