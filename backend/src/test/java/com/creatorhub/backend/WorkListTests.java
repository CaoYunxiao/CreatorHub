package com.creatorhub.backend;

import java.time.LocalDateTime;
import java.util.List;

import com.creatorhub.backend.controller.WorkController;
import com.creatorhub.backend.entity.WorkSummary;
import com.creatorhub.backend.exception.GlobalExceptionHandler;
import com.creatorhub.backend.repository.WorkRepository;
import com.creatorhub.backend.service.WorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkListTests {
    private WorkRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(WorkRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkController(new WorkService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void defaultsReturnAnEmptyPage() throws Exception {
        when(repository.findPage(20, 0L)).thenReturn(List.of());
        mvc.perform(get("/api/works")).andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items").isEmpty());
        verify(repository).findPage(20, 0L);
    }

    @Test
    void mapsRecordsAndCalculatesOffset() throws Exception {
        var time = LocalDateTime.of(2026, 11, 4, 12, 0);
        when(repository.findPage(5, 10L)).thenReturn(
                List.of(new WorkSummary(7, 9, "作品", null, time, time)));
        mvc.perform(get("/api/works?page=3&size=5")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].authorId").value(9))
                .andExpect(jsonPath("$.items[0].title").value("作品"))
                .andExpect(jsonPath("$.items[0].content").doesNotExist());
        verify(repository).findPage(5, 10L);
    }

    @Test
    void invalidParametersNeverReachDatabase() throws Exception {
        for (String query : List.of("page=0", "page=-1", "size=0", "size=101", "page=text",
                "size=2147483648")) {
            mvc.perform(get("/api/works?" + query)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void largePageDoesNotOverflowOffset() throws Exception {
        when(repository.findPage(100, 214748364600L)).thenReturn(List.of());
        mvc.perform(get("/api/works?page=2147483647&size=100")).andExpect(status().isOk());
        verify(repository).findPage(100, 214748364600L);
    }

    @Test
    void databaseErrorsDoNotLeakDriverDetails() throws Exception {
        when(repository.findPage(20, 0L)).thenThrow(
                new DataAccessResourceFailureException("secret-connection-detail"));
        mvc.perform(get("/api/works")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("secret-connection-detail"))));
    }

    @Test
    void preservesHttpStatusForUnsupportedMethodAndMissingPath() throws Exception {
        mvc.perform(post("/api/works")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/unknown")).andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }
}
