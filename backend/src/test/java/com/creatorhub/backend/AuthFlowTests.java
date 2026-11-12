package com.creatorhub.backend;

import java.util.Optional;

import com.creatorhub.backend.entity.UserAccount;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.repository.WorkRepository;
import com.creatorhub.backend.repository.WorkImageRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
@AutoConfigureMockMvc
class AuthFlowTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserRepository users;
    @MockitoBean WorkRepository works;
    @MockitoBean WorkImageRepository images;

    @Test
    void registrationStoresHashAndDoesNotAutomaticallyLogin() throws Exception {
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(users.insert(eq("alice"), anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(1);
            assertNotEquals("example-pass-123", hash);
            assertTrue(encoder.matches("example-pass-123", hash));
            return 7L;
        });
        var result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Alice\",\"password\":\"example-pass-123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andReturn();
        mvc.perform(get("/api/auth/me").session((MockHttpSession) result.getRequest().getSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfLoginSessionRotationCurrentUserAndLogout() throws Exception {
        when(users.findByUsername("alice")).thenReturn(Optional.of(
                new UserAccount(7, "alice", encoder.encode("example-pass-123"))));
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) csrfResult.getRequest().getSession();
        String oldId = session.getId();
        String body = csrfResult.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        String header = JsonPath.read(body, "$.headerName");

        mvc.perform(post("/api/auth/login").session(session).header(header, token)
                        .with(request -> {
                            // 模拟浏览器确实携带有效 Session Cookie；仅 .session() 不设置此标志。
                            request.setRequestedSessionId(oldId);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        })
                        .param("username", "ALICE").param("password", "example-pass-123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
        assertNotEquals(oldId, session.getId(), "登录后必须更换 Session ID");
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(content().string(not(containsString("password"))));

        // 登录后旧 CSRF token 必须失效。
        mvc.perform(post("/api/auth/logout").session(session).header(header, token))
                .andExpect(status().isForbidden());
        var fresh = mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
        String freshToken = JsonPath.read(fresh.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/logout").session(session).header(header, freshToken))
                .andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingCsrfBlocksRegistrationAndLogin() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"example-pass-123\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").param("username", "alice").param("password", "wrong"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void wrongAndUnknownCredentialsHaveSameResponse() throws Exception {
        when(users.findByUsername("alice")).thenReturn(Optional.of(
                new UserAccount(7, "alice", encoder.encode("example-pass-123"))));
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());
        var wrong = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized()).andReturn();
        var unknown = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "unknown").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized()).andReturn();
        assertEquals(wrong.getResponse().getContentAsString(), unknown.getResponse().getContentAsString());
    }

    @Test
    void duplicateAndInvalidRegistration() throws Exception {
        when(users.findByUsername("alice")).thenReturn(Optional.of(new UserAccount(7, "alice", "unused")));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"example-pass-123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"password\":\"example-pass-123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"valid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verify(users, never()).insert(anyString(), anyString());
    }

    @Test
    void anonymousWorkListRemainsAvailable() throws Exception {
        mvc.perform(get("/api/works")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedLoginPasswordDoesNotCauseServerError() throws Exception {
        when(users.findByUsername("alice")).thenReturn(Optional.of(
                new UserAccount(7, "alice", encoder.encode("example-pass-123"))));
        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "alice").param("password", "x".repeat(73)))
                .andExpect(status().isUnauthorized());
    }
}
