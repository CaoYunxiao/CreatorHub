package com.creatorhub.backend;

import java.util.Optional;

import com.creatorhub.backend.dto.RegisterRequest;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.UsernameTakenException;
import com.creatorhub.backend.repository.UserRepository;
import com.creatorhub.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTests {
    @Test
    void concurrentDuplicateIsReportedAsConflict() {
        var repository = mock(UserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        when(repository.findByUsername("alice")).thenReturn(Optional.empty());
        when(encoder.encode("example-pass-123")).thenReturn("test-hash");
        when(repository.insert("alice", "test-hash")).thenThrow(new DuplicateKeyException("internal"));
        var service = new UserService(repository, encoder);
        assertThrows(UsernameTakenException.class,
                () -> service.register(new RegisterRequest("Alice", "example-pass-123")));
    }

    @Test
    void rejectsUtf8PasswordsOverBcryptLimitBeforeAccessingDatabase() {
        var repository = mock(UserRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var service = new UserService(repository, encoder);
        assertThrows(InvalidRequestException.class,
                () -> service.register(new RegisterRequest("alice", "密".repeat(25))));
        assertThrows(InvalidRequestException.class,
                () -> service.register(new RegisterRequest("alice", null)));
        verifyNoInteractions(repository, encoder);
    }
}
