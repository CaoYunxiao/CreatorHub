package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.util.List;

import com.creatorhub.backend.dto.WorkImageResponse;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkImageRepository {
    private final JdbcTemplate jdbc;

    public WorkImageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WorkImageResponse> findByWorkId(long workId) {
        return jdbc.query("""
                SELECT id, image_url, sort_order FROM work_images
                WHERE work_id = ? ORDER BY sort_order, id
                """, (row, number) -> new WorkImageResponse(row.getLong("id"),
                row.getString("image_url"), row.getInt("sort_order")), workId);
    }

    public boolean isLinked(long workId, String imageUrl) {
        return !jdbc.query("""
                SELECT id FROM work_images WHERE work_id = ? AND image_url = ? LIMIT 1
                """, (row, number) -> row.getLong("id"), workId, imageUrl).isEmpty();
    }

    @Transactional
    public long insert(long workId, String imageUrl) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO work_images (work_id, image_url, sort_order) VALUES (?, ?, 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, workId);
            statement.setString(2, imageUrl);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new DataRetrievalFailureException("未取得图片 ID");
        }
        return id.longValue();
    }
}
