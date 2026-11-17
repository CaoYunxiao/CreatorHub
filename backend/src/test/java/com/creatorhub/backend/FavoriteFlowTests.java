package com.creatorhub.backend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.creatorhub.backend.dto.WorkSummaryResponse;
import com.creatorhub.backend.entity.UserAccount;
import com.creatorhub.backend.repository.FavoriteRepository;
import com.creatorhub.backend.repository.TagRepository;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class FavoriteFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean WorkRepository works;
    @MockitoBean WorkImageRepository images;
    @MockitoBean TagRepository tags;
    @MockitoBean FavoriteRepository favorites;

    @Test
    void frontendAssetsArePublicWhileFavoritesRemainPrivate() throws Exception {
        for (String path : List.of("/app/index.html", "/app/styles.css", "/app/app.js")) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/app/other.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRequestsAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/me/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/works/7/favorite").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/works/7/favorite").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/works/7/favorite").with(user("reader"))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/works/7/favorite").with(user("reader"))).andExpect(status().isForbidden());
        verifyNoInteractions(users, works, favorites);
    }

    @Test
    void duplicateInsertIsIdempotentAndMissingWorkReturns404() throws Exception {
        givenUser("reader", 9);
        when(favorites.insert(9L, 7L)).thenReturn(1).thenThrow(new DuplicateKeyException("duplicate"));

        mvc.perform(put("/api/works/7/favorite").with(user("reader")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/works/7/favorite").with(user("reader")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/works/8/favorite").with(user("reader")).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORK_NOT_FOUND"));
        verify(favorites, times(2)).insert(9L, 7L);
        verify(favorites).insert(9L, 8L);
        verifyNoMoreInteractions(favorites);
    }

    @Test
    void listsAndDeletesOnlyForTheAuthenticatedUser() throws Exception {
        givenUser("reader", 9);
        givenUser("other", 12);
        var time = LocalDateTime.of(2026, 11, 17, 12, 0);
        when(favorites.findPage(9L, 5, 5L)).thenReturn(List.of(
                new WorkSummaryResponse(7, 12, "标题", null, time, time)));

        mvc.perform(get("/api/me/favorites?page=2&size=5&userId=12").with(user("reader")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].id").value(7));
        mvc.perform(get("/api/me/favorites?page=2&size=5&userId=9").with(user("other")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete("/api/works/7/favorite?userId=9").with(user("other")).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        verify(favorites).findPage(9L, 5, 5L);
        verify(favorites).findPage(12L, 5, 5L);
        verify(favorites, times(2)).delete(12L, 7L);
        verifyNoMoreInteractions(favorites);
    }

    @Test
    void validatesPaginationAndWorkIdBeforeDatabaseAccess() throws Exception {
        mvc.perform(get("/api/me/favorites?page=0").with(user("reader"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/favorites?size=101").with(user("reader"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/works/0/favorite").with(user("reader")).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/works/0/favorite").with(user("reader")).with(csrf()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users, works, favorites);
    }

    @Test
    void sqlScopesReadsAndDeletesAndSortsByFavoriteTime() {
        var jdbc = mock(JdbcTemplate.class);
        var repository = new FavoriteRepository(jdbc);
        repository.findPage(9L, 5, 10L);
        repository.delete(9L, 7L);

        verify(jdbc).query(argThat(sql -> sql.contains("WHERE f.user_id = ?")
                        && sql.contains("w.deleted_at IS NULL")
                        && sql.contains("ORDER BY f.created_at DESC, f.id DESC")),
                org.mockito.ArgumentMatchers.<RowMapper<WorkSummaryResponse>>any(), eq(9L), eq(5), eq(10L));
        verify(jdbc).update(argThat(sql -> sql.contains("WHERE user_id = ? AND work_id = ?")
                && sql.contains("w.deleted_at IS NULL")), eq(9L), eq(7L));
    }

    private void givenUser(String username, long id) {
        when(users.findByUsername(username)).thenReturn(Optional.of(new UserAccount(id, username, "test-hash")));
    }
}
