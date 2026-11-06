package com.creatorhub.backend;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/** 只读验收：不启动业务组件，不执行数据库初始化脚本。 */
@EnabledIfEnvironmentVariable(named = "CREATORHUB_DB_VERIFY", matches = "true")
class DatabaseConnectionTests {

    @Test
    void existingDatabaseHasExpectedColumns() {
        String url = requiredEnvironment("CREATORHUB_DB_URL");
        assertTrue(url.startsWith("jdbc:mysql:"), "仅允许 MySQL JDBC URL");
        Properties properties = new Properties();
        properties.setProperty("user", requiredEnvironment("CREATORHUB_DB_USERNAME"));
        properties.setProperty("password", requiredEnvironment("CREATORHUB_DB_PASSWORD"));
        properties.setProperty("connectTimeout", "5000");
        properties.setProperty("socketTimeout", "10000");

        Map<String, Set<String>> expected = Map.of(
                "users", Set.of("id", "username", "password_hash", "created_at"),
                "works", Set.of("id", "user_id", "title", "intro", "content", "created_at", "updated_at"),
                "work_images", Set.of("id", "work_id", "image_url", "sort_order"),
                "tags", Set.of("id", "name"),
                "work_tags", Set.of("work_id", "tag_id"),
                "favorites", Set.of("id", "user_id", "work_id", "created_at"));

        try (var connection = DriverManager.getConnection(url, properties);
             var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var result = statement.executeQuery("SELECT 1, DATABASE()")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
                assertEquals("creatorhub", result.getString(2), "必须连接现有 creatorhub 数据库");
            }
            Map<String, Set<String>> actual = new HashMap<>();
            try (var result = statement.executeQuery(
                    "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()")) {
                while (result.next()) {
                    actual.computeIfAbsent(result.getString(1), key -> new HashSet<>()).add(result.getString(2));
                }
            }
            expected.forEach((table, columns) -> assertEquals(columns, actual.get(table), "表结构不符：" + table));
        } catch (SQLException exception) {
            // 不输出可能携带连接配置的驱动异常原文或异常链。
            fail("数据库只读验收失败；SQLState=" + exception.getSQLState()
                    + "，错误码=" + exception.getErrorCode());
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        assertNotNull(value, "缺少环境变量：" + name);
        assertFalse(value.isBlank(), "环境变量为空：" + name);
        return value;
    }
}
