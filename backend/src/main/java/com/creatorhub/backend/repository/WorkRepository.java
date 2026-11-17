package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.creatorhub.backend.entity.WorkDetail;
import com.creatorhub.backend.entity.WorkSummary;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRepository {
    private static final RowMapper<WorkSummary> SUMMARY_ROW = (row, number) -> new WorkSummary(
            row.getLong("id"), row.getLong("user_id"), row.getString("title"), row.getString("intro"),
            row.getObject("created_at", LocalDateTime.class), row.getObject("updated_at", LocalDateTime.class));

    private final JdbcTemplate jdbcTemplate;

    public WorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<WorkSummary> findPage(int limit, long offset) {
        // 排序字段固定，分页参数绑定，不把请求参数拼入 SQL。
        return jdbcTemplate.query("""
                SELECT id, user_id, title, intro, created_at, updated_at
                FROM works WHERE deleted_at IS NULL
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """, SUMMARY_ROW, limit, offset);
    }

    public List<WorkSummary> searchPage(String keyword, Long tagId, int limit, long offset) {
        var sql = new StringBuilder("""
                SELECT w.id, w.user_id, w.title, w.intro, w.created_at, w.updated_at
                FROM works w WHERE w.deleted_at IS NULL
                """);
        var arguments = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND w.title LIKE ? ESCAPE '!'");
            arguments.add("%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        if (tagId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM work_tags wt WHERE wt.work_id = w.id AND wt.tag_id = ?)");
            arguments.add(tagId);
        }
        sql.append(" ORDER BY w.created_at DESC, w.id DESC LIMIT ? OFFSET ?");
        arguments.add(limit);
        arguments.add(offset);
        return jdbcTemplate.query(sql.toString(), SUMMARY_ROW, arguments.toArray());
    }

    public Optional<Long> findAuthorId(long id) {
        return jdbcTemplate.query("SELECT user_id FROM works WHERE id = ? AND deleted_at IS NULL",
                (row, number) -> row.getLong("user_id"), id).stream().findFirst();
    }

    public Optional<Long> lockAuthorId(long id) {
        return jdbcTemplate.query("SELECT user_id FROM works WHERE id = ? AND deleted_at IS NULL FOR UPDATE",
                (row, number) -> row.getLong("user_id"), id).stream().findFirst();
    }

    public Optional<WorkDetail> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, user_id, title, intro, content, created_at, updated_at
                FROM works WHERE id = ? AND deleted_at IS NULL
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
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, title, intro, content, id, authorId);
    }

    public int markDeletedByAuthor(long id, long authorId) {
        return jdbcTemplate.update("""
                UPDATE works SET deleted_at = CURRENT_TIMESTAMP
                WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id, authorId);
    }
}
