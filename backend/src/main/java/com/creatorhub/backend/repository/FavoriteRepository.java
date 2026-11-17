package com.creatorhub.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.backend.dto.WorkSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FavoriteRepository {
    private final JdbcTemplate jdbc;

    public FavoriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(long userId, long workId) {
        jdbc.update("INSERT INTO favorites (user_id, work_id) VALUES (?, ?)", userId, workId);
    }

    public void delete(long userId, long workId) {
        jdbc.update("DELETE FROM favorites WHERE user_id = ? AND work_id = ?", userId, workId);
    }

    public List<WorkSummaryResponse> findPage(long userId, int limit, long offset) {
        return jdbc.query("""
                SELECT w.id, w.user_id, w.title, w.intro, w.created_at, w.updated_at
                FROM favorites f JOIN works w ON w.id = f.work_id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC, f.id DESC
                LIMIT ? OFFSET ?
                """, (row, number) -> new WorkSummaryResponse(row.getLong("id"), row.getLong("user_id"),
                row.getString("title"), row.getString("intro"),
                row.getObject("created_at", LocalDateTime.class), row.getObject("updated_at", LocalDateTime.class)),
                userId, limit, offset);
    }
}
