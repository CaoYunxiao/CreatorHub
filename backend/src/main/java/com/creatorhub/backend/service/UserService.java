package com.creatorhub.backend.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.creatorhub.backend.dto.RegisterRequest;
import com.creatorhub.backend.dto.UserResponse;
import com.creatorhub.backend.exception.InvalidRequestException;
import com.creatorhub.backend.exception.UsernameTakenException;
import com.creatorhub.backend.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserDetailsService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse register(RegisterRequest request) {
        String username = canonicalUsername(request.username());
        String password = request.password();
        if (password == null || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRequestException("密码至少 8 个字符，UTF-8 编码长度不得超过 72 字节");
        }
        if (repository.findByUsername(username).isPresent()) {
            throw new UsernameTakenException();
        }
        String hash = passwordEncoder.encode(password);
        try {
            return new UserResponse(repository.insert(username, hash), username);
        } catch (DuplicateKeyException exception) {
            // 唯一索引负责处理并发注册，响应不包含数据库异常原文。
            throw new UsernameTakenException();
        }
    }

    @Override
    public UserDetails loadUserByUsername(String input) {
        String username;
        try {
            username = canonicalUsername(input);
        } catch (InvalidRequestException exception) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        var account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
        // Spring Security 的 User 会在认证后擦除其密码哈希。
        return User.withUsername(account.username()).password(account.passwordHash()).roles("USER").build();
    }

    public UserResponse currentUser(String username) {
        var account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在或登录已失效"));
        return new UserResponse(account.id(), account.username());
    }

    private String canonicalUsername(String username) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,50}")) {
            throw new InvalidRequestException("用户名需为 3 到 50 位英文字母、数字或下划线");
        }
        return username.toLowerCase(Locale.ROOT);
    }
}
