package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.util.List;

import com.creatorhub.backend.dto.WorkImageResponse;
import com.creatorhub.backend.exception.WorkNotFoundException;
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
                SELECT wi.id, wi.image_url, wi.sort_order FROM work_images wi
                JOIN works w ON w.id = wi.work_id
                WHERE wi.work_id = ? AND w.deleted_at IS NULL ORDER BY wi.sort_order, wi.id
                """, (row, number) -> new WorkImageResponse(row.getLong("id"),
                row.getString("image_url"), row.getInt("sort_order")), workId);
    }

    public boolean isLinked(long workId, String imageUrl) {
        return !jdbc.query("""
                SELECT wi.id FROM work_images wi JOIN works w ON w.id = wi.work_id
                WHERE wi.work_id = ? AND wi.image_url = ? AND w.deleted_at IS NULL LIMIT 1
                """, (row, number) -> row.getLong("id"), workId, imageUrl).isEmpty();
    }

    @Transactional
    public long insert(long workId, String imageUrl) {
        var keys = new GeneratedKeyHolder();
        int inserted = jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO work_images (work_id, image_url, sort_order)
                    SELECT id, ?, 0 FROM works WHERE id = ? AND deleted_at IS NULL
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, imageUrl);
            statement.setLong(2, workId);
            return statement;
        }, keys);
        if (inserted == 0) {
            throw new WorkNotFoundException();
        }
        Number id = keys.getKey();
        if (id == null) {
            throw new DataRetrievalFailureException("未取得图片 ID");
        }
        return id.longValue();
    }
}
