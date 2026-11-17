package com.creatorhub.backend;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.backend.entity.WorkSummary;
import com.creatorhub.backend.repository.TagRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkRepositoryTests {
    @Test
    void bindsLiteralKeywordAndTagFilterTogether() {
        var jdbc = mock(JdbcTemplate.class);
        new WorkRepository(jdbc).searchPage("100%_!", 3L, 20, 40L);
        verify(jdbc).query(argThat(sql -> sql.contains("LIKE ? ESCAPE '!' AND EXISTS")
                        && sql.contains("w.deleted_at IS NULL")),
                org.mockito.ArgumentMatchers.<RowMapper<WorkSummary>>any(),
                eq("%100!%!_!!%"), eq(3L), eq(20), eq(40L));
    }

    @Test
    void softDeleteScopesTheUpdateAndFiltersPublicWorkAndMediaReads() {
        var jdbc = mock(JdbcTemplate.class);
        var works = new WorkRepository(jdbc);
        works.markDeletedByAuthor(7L, 9L);
        works.findById(7L);
        works.findAuthorId(7L);
        works.lockAuthorId(7L);
        var images = new WorkImageRepository(jdbc);
        images.findByWorkId(7L);
        images.isLinked(7L, "/image.png");
        new TagRepository(jdbc).findByWorkId(7L);

        verify(jdbc).update(argThat(sql -> sql.contains("SET deleted_at = CURRENT_TIMESTAMP")
                && sql.contains("WHERE id = ? AND user_id = ? AND deleted_at IS NULL")), eq(7L), eq(9L));
        verify(jdbc, times(3)).query(contains("WHERE id = ? AND deleted_at IS NULL"), any(RowMapper.class), eq(7L));
        verify(jdbc, times(2)).query(argThat(sql -> sql.contains("JOIN works w")
                && sql.contains("w.deleted_at IS NULL")), any(RowMapper.class), eq(7L));
        verify(jdbc).query(contains("w.deleted_at IS NULL"), any(RowMapper.class), eq(7L), eq("/image.png"));
    }

    @Test
    void updateBindsBothWorkIdAndAuthorIdInTheSql() {
        var jdbc = mock(JdbcTemplate.class);
        new WorkRepository(jdbc).updateByAuthor(7L, 9L, "标题", null, "正文");
        verify(jdbc).update(contains("WHERE id = ? AND user_id = ?"), eq("标题"), isNull(),
                eq("正文"), eq(7L), eq(9L));
    }

    @Test
    void bindsPaginationAndMapsNullableIntroWithoutLoadingContent() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var row = mock(ResultSet.class);
        var time = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(row.getLong("id")).thenReturn(7L);
        when(row.getLong("user_id")).thenReturn(9L);
        when(row.getString("title")).thenReturn("作品");
        when(row.getObject("created_at", LocalDateTime.class)).thenReturn(time);
        when(row.getObject("updated_at", LocalDateTime.class)).thenReturn(time);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<WorkSummary>>any(),
                eq(20), eq(40L))).thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    assertTrue(sql.contains("LIMIT ? OFFSET ?"));
                    assertTrue(sql.contains("deleted_at IS NULL"));
                    assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"));
                    RowMapper<WorkSummary> mapper = invocation.getArgument(1);
                    return List.of(mapper.mapRow(row, 0));
                });

        var result = new WorkRepository(jdbc).findPage(20, 40L);
        assertEquals(List.of(new WorkSummary(7, 9, "作品", null, time, time)), result);
        verify(row, never()).getString("content");
    }
}
