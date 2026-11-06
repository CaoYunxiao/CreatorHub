package com.creatorhub.backend.repository;

import java.sql.Statement;
import java.util.Optional;

import com.creatorhub.backend.entity.UserAccount;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserAccount> findByUsername(String username) {
        return jdbc.query("SELECT id, username, password_hash FROM users WHERE username = ?",
                (row, number) -> new UserAccount(row.getLong("id"), row.getString("username"),
                        row.getString("password_hash")), username).stream().findFirst();
    }

    public long insert(String username, String passwordHash) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new DataRetrievalFailureException("未取得用户 ID");
        }
        return id.longValue();
    }
}
