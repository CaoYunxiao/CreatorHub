package com.creatorhub.backend;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.creatorhub.backend.dto.TagResponse;
import com.creatorhub.backend.entity.UserAccount;
import com.creatorhub.backend.repository.TagRepository;
import com.creatorhub.backend.repository.FavoriteRepository;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.creatorhub.backend.repository.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class TagFlowTests {
    @MockitoBean FavoriteRepository favorites;
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean WorkRepository works;
    @MockitoBean WorkImageRepository images;
    @MockitoBean TagRepository tags;
    @MockitoBean DataSource dataSource;
    private Connection connection;

    @TestConfiguration
    @EnableTransactionManagement
    static class Transactions {
        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @BeforeEach
    void configureDatabase() throws Exception {
        connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
        when(users.findByUsername("author")).thenReturn(Optional.of(new UserAccount(9, "author", "test-hash")));
        when(works.lockAuthorId(7L)).thenReturn(Optional.of(9L));
    }

    @Test
    void listsPublicTagsAndFiltersWorks() throws Exception {
        var tag = new TagResponse(3, "摄影");
        when(tags.list()).thenReturn(List.of(tag));
        when(works.findAuthorId(7L)).thenReturn(Optional.of(9L));
        when(tags.findByWorkId(7L)).thenReturn(List.of(tag));
        mvc.perform(get("/api/tags")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("摄影"));
        mvc.perform(get("/api/works/7/tags")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(3));
        mvc.perform(get("/api/works/8/tags")).andExpect(status().isNotFound());
        mvc.perform(get("/api/works").param("q", " 摄影 ").param("tagId", "3").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2));
        verify(works).searchPage("摄影", 3L, 20, 20L);
        mvc.perform(get("/api/works").param("tagId", "0")).andExpect(status().isBadRequest());
    }

    @Test
    void creationRequiresLoginAndCsrfAndReportsDuplicateNames() throws Exception {
        String payload = "{\"name\":\" 摄影 \"}";
        mvc.perform(post("/api/tags").with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tags").with(user("author")).contentType("application/json").content(payload))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tags);

        when(tags.insert("摄影")).thenReturn(3L).thenThrow(new DuplicateKeyException("duplicate"));
        mvc.perform(post("/api/tags").with(user("author")).with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(3));
        mvc.perform(post("/api/tags").with(user("author")).with(csrf()).contentType("application/json").content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAG_NAME_TAKEN"));
    }

    @Test
    void rejectsUnknownAndDuplicateIdsBeforeReplacingAnyAssociation() throws Exception {
        replace("[3,3]").andExpect(status().isBadRequest());
        replace("[null]").andExpect(status().isBadRequest());
        replace("[999]").andExpect(status().isBadRequest());
        verify(tags).allExist(List.of(999L));
        verify(tags, never()).replaceForWork(anyLong(), anyList());
    }

    @Test
    void onlyAuthorMayReplaceOrClearTags() throws Exception {
        when(works.lockAuthorId(7L)).thenReturn(Optional.of(10L), Optional.of(9L));
        replace("[3]").andExpect(status().isForbidden());
        verifyNoInteractions(tags);
        when(tags.allExist(List.of())).thenReturn(true);
        replace("[]").andExpect(status().isNoContent());
        verify(tags).replaceForWork(7L, List.of());
        verify(connection).commit();
    }

    @Test
    void replacementFailureRollsBackTheJdbcTransaction() throws Exception {
        when(tags.allExist(List.of(3L))).thenReturn(true);
        doThrow(new DataAccessResourceFailureException("simulated replacement failure"))
                .when(tags).replaceForWork(7L, List.of(3L));
        replace("[3]").andExpect(status().isServiceUnavailable());
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    private org.springframework.test.web.servlet.ResultActions replace(String ids) throws Exception {
        return mvc.perform(put("/api/works/7/tags").with(user("author")).with(csrf())
                .contentType("application/json").content("{\"tagIds\":" + ids + "}"));
    }
}
