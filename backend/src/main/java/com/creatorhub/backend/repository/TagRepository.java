package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import com.creatorhub.backend.dto.TagResponse;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TagRepository {
    private static final RowMapper<TagResponse> TAG_ROW =
            (row, number) -> new TagResponse(row.getLong("id"), row.getString("name"));

    private final JdbcTemplate jdbc;

    public TagRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TagResponse> list() {
        return jdbc.query("SELECT id, name FROM tags ORDER BY name, id", TAG_ROW);
    }

    public long insert(String name) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO tags (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new DataRetrievalFailureException("未取得标签 ID");
        }
        return key.longValue();
    }

    public List<TagResponse> findByWorkId(long workId) {
        return jdbc.query("""
                SELECT t.id, t.name FROM tags t
                JOIN work_tags wt ON wt.tag_id = t.id
                JOIN works w ON w.id = wt.work_id
                WHERE wt.work_id = ? AND w.deleted_at IS NULL ORDER BY t.name, t.id
                """, TAG_ROW, workId);
    }

    public boolean allExist(List<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return true;
        }
        // 仅生成占位符，ID 仍由 JDBC 绑定。
        String placeholders = String.join(", ", Collections.nCopies(tagIds.size(), "?"));
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tags WHERE id IN (" + placeholders + ")",
                Long.class, tagIds.toArray());
        return count != null && count == tagIds.size();
    }

    public void replaceForWork(long workId, List<Long> tagIds) {
        jdbc.update("DELETE FROM work_tags WHERE work_id = ?", workId);
        addForWork(workId, tagIds);
    }

    public void addForWork(long workId, List<Long> tagIds) {
        if (!tagIds.isEmpty()) {
            List<Object[]> rows = tagIds.stream().map(tagId -> new Object[]{workId, tagId}).toList();
            jdbc.batchUpdate("INSERT INTO work_tags (work_id, tag_id) VALUES (?, ?)", rows);
        }
    }
}
