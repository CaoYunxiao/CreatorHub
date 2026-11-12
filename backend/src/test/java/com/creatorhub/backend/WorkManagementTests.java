package com.creatorhub.backend;

import java.time.LocalDateTime;
import java.util.Optional;

import com.creatorhub.backend.entity.UserAccount;
import com.creatorhub.backend.entity.WorkDetail;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.repository.WorkRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class WorkManagementTests {
    private static final String WORK_JSON = """
            {"title":"标题","content":"正文"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean WorkRepository works;
    @MockitoBean WorkImageRepository images;

    @Test
    void anonymousCanReadWorkDetailAndMissingWorkReturns404() throws Exception {
        when(works.findById(7L)).thenReturn(Optional.of(work()));
        mvc.perform(get("/api/works/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorId").value(9))
                .andExpect(jsonPath("$.content").value("正文"));
        mvc.perform(get("/api/works/8")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_NOT_FOUND"));
    }

    @Test
    void creationUsesLoggedInAuthorAndReturnsLocation() throws Exception {
        givenUser("author", 9L);
        when(works.insert(9L, "标题", null, "正文")).thenReturn(7L);
        mvc.perform(post("/api/works").with(user("author")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"authorId":12,"title":" 标题 ","content":"正文"}
                                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/works/7"));
        verify(works).insert(9L, "标题", null, "正文");
    }

    @Test
    void ownerCanUpdateEvenWhenValuesAreUnchanged() throws Exception {
        givenUser("author", 9L);
        when(works.updateByAuthor(7L, 9L, "标题", null, "正文")).thenReturn(1, 0);
        when(works.findById(7L)).thenReturn(Optional.of(work()));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(put("/api/works/7").with(user("author")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(WORK_JSON))
                    .andExpect(status().isNoContent());
        }
        verify(works, times(2)).updateByAuthor(7L, 9L, "标题", null, "正文");
    }

    @Test
    void anotherUserCannotUpdateAndUnknownWorkReturns404() throws Exception {
        givenUser("other", 12L);
        when(works.findById(7L)).thenReturn(Optional.of(work()));
        mvc.perform(put("/api/works/7").with(user("other")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(WORK_JSON))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("WORK_FORBIDDEN"));
        mvc.perform(put("/api/works/8").with(user("other")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(WORK_JSON))
                .andExpect(status().isNotFound());
        verify(works).updateByAuthor(7L, 12L, "标题", null, "正文");
        verify(works, never()).updateByAuthor(eq(7L), eq(9L), any(), any(), any());
    }

    @Test
    void unauthenticatedAndCsrfFailuresDoNotReachRepositories() throws Exception {
        mvc.perform(post("/api/works").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(WORK_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/works/7").with(user("author"))
                        .contentType(MediaType.APPLICATION_JSON).content(WORK_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, works);
    }

    @Test
    void invalidIdAndOversizedUtf8ContentDoNotReachRepositories() throws Exception {
        mvc.perform(get("/api/works/0")).andExpect(status().isBadRequest());
        String oversized = "{\"title\":\"标题\",\"content\":\"" + "文".repeat(21_846) + "\"}";
        mvc.perform(post("/api/works").with(user("author")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(users, works);
    }

    private void givenUser(String username, long id) {
        when(users.findByUsername(username)).thenReturn(Optional.of(new UserAccount(id, username, "test-hash")));
    }

    private WorkDetail work() {
        var time = LocalDateTime.of(2026, 11, 12, 12, 0);
        return new WorkDetail(7L, 9L, "标题", null, "正文", time, time);
    }
}
