package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.creatorhub.backend.entity.WorkDetail;
import com.creatorhub.backend.entity.WorkSummary;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
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

    public Optional<WorkDetail> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, user_id, title, intro, content, created_at, updated_at
                FROM works WHERE id = ?
                """, (row, number) -> new WorkDetail(
                row.getLong("id"), row.getLong("user_id"), row.getString("title"),
                row.getString("intro"), row.getString("content"),
                row.getObject("created_at", LocalDateTime.class),
                row.getObject("updated_at", LocalDateTime.class)), id).stream().findFirst();
    }

    public long insert(long authorId, String title, String intro, String content) {
        var keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO works (user_id, title, intro, content) VALUES (?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, authorId);
            statement.setString(2, title);
            statement.setString(3, intro);
            statement.setString(4, content);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new DataRetrievalFailureException("未取得作品 ID");
        }
        return id.longValue();
    }

    public int updateByAuthor(long id, long authorId, String title, String intro, String content) {
        return jdbcTemplate.update("""
                UPDATE works SET title = ?, intro = ?, content = ?
                WHERE id = ? AND user_id = ?
                """, title, intro, content, id, authorId);
    }
}
