package com.creatorhub.backend;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import com.creatorhub.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserRepositoryTests {
    @Test
    void registrationBindsOnlyUsernameAndHashAndReturnsGeneratedId() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS))
                .thenReturn(statement);
        when(jdbc.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            assertSame(statement, creator.createPreparedStatement(connection));
            KeyHolder keys = invocation.getArgument(1);
            keys.getKeyList().addAll(List.of(Map.of("id", 7L)));
            return 1;
        });
        assertEquals(7L, new UserRepository(jdbc).insert("alice", "test-hash"));
        verify(statement).setString(1, "alice");
        verify(statement).setString(2, "test-hash");
        verifyNoMoreInteractions(statement);
    }
}
