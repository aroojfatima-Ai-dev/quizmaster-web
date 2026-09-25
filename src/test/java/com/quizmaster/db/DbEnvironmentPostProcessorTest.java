package com.quizmaster.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection resolver: Railway's Postgres plugin, Railway's MySQL plugin,
 * plain {@code DB_*} variables, and the zero-setup H2 fallback.
 */
class DbEnvironmentPostProcessorTest {

    @Test
    @DisplayName("a Railway Postgres DATABASE_URL becomes a JDBC URL")
    void railwayPostgresUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://quiz:secret%40pass@containers-us-west-1.railway.app:6543/railway");

        Map<String, Object> resolved = DbEnvironmentPostProcessor.resolve(environment);

        assertEquals("jdbc:postgresql://containers-us-west-1.railway.app:6543/railway?sslmode=prefer",
                resolved.get("spring.datasource.url"));
        assertEquals("quiz", resolved.get("spring.datasource.username"));
        assertEquals("secret@pass", resolved.get("spring.datasource.password"));
        assertEquals("org.postgresql.Driver", resolved.get("spring.datasource.driver-class-name"));
    }

    @Test
    @DisplayName("query parameters Railway already put in the URL are kept")
    void keepsExistingQueryParameters() {
        assertEquals("jdbc:postgresql://host:5432/db?sslmode=require",
                DbEnvironmentPostProcessor.postgresJdbcUrl("host", "5432", "db", "sslmode=require"));
        assertEquals("jdbc:postgresql://host:5432/db?sslmode=prefer",
                DbEnvironmentPostProcessor.postgresJdbcUrl("host", "5432", "db", null));
    }

    @Test
    @DisplayName("a MySQL URL gets the utf8mb4 settings the Urdu text needs")
    void mysqlUrl() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MYSQL_URL", "mysql://root:pw@mysql.railway.internal:3306/railway");

        Map<String, Object> resolved = DbEnvironmentPostProcessor.resolve(environment);
        String url = (String) resolved.get("spring.datasource.url");

        assertTrue(url.startsWith("jdbc:mysql://mysql.railway.internal:3306/railway?"));
        assertTrue(url.contains("characterEncoding=UTF-8"));
        assertTrue(url.contains("allowPublicKeyRetrieval=true"));
        assertEquals("com.mysql.cj.jdbc.Driver", resolved.get("spring.datasource.driver-class-name"));
        assertEquals("root", resolved.get("spring.datasource.username"));
        assertEquals("pw", resolved.get("spring.datasource.password"));
    }

    @Test
    @DisplayName("Railway's separate MySQL variables work too")
    void mysqlPieces() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("MYSQLHOST", "mysql.railway.internal")
                .withProperty("MYSQLPORT", "3306")
                .withProperty("MYSQLUSER", "root")
                .withProperty("MYSQLPASSWORD", "hunter2")
                .withProperty("MYSQLDATABASE", "railway");

        Map<String, Object> resolved = DbEnvironmentPostProcessor.resolve(environment);

        assertTrue(((String) resolved.get("spring.datasource.url")).contains("jdbc:mysql://mysql.railway.internal:3306/railway"));
        assertEquals("hunter2", resolved.get("spring.datasource.password"));
    }

    @Test
    @DisplayName("plain DB_* variables are used when nothing else is set")
    void genericVariables() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_HOST", "db.example.com")
                .withProperty("DB_PORT", "5433")
                .withProperty("DB_NAME", "quizmaster")
                .withProperty("DB_USER", "quiz")
                .withProperty("DB_PASSWORD", "pw");

        Map<String, Object> resolved = DbEnvironmentPostProcessor.resolve(environment);

        assertEquals("jdbc:mysql://db.example.com:5433/quizmaster"
                        + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
                        + "&connectionCollation=utf8mb4_unicode_ci&serverTimezone=UTC&connectTimeout=10000",
                resolved.get("spring.datasource.url"));
        assertEquals("quiz", resolved.get("spring.datasource.username"));
    }

    @Test
    @DisplayName("with no database variables at all, the app still starts on H2")
    void h2Fallback() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("QUIZMASTER_DATA_DIR", "/app/data");

        Map<String, Object> resolved = DbEnvironmentPostProcessor.resolve(environment);

        assertEquals("jdbc:h2:file:/app/data/quizmaster;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE",
                resolved.get("spring.datasource.url"));
        assertEquals("org.h2.Driver", resolved.get("spring.datasource.driver-class-name"));
        assertEquals("sa", resolved.get("spring.datasource.username"));
    }

    @Test
    void urlDialectDetection() {
        assertTrue(DbEnvironmentPostProcessor.isPostgresUrl("postgres://a:b@c:5432/d"));
        assertTrue(DbEnvironmentPostProcessor.isPostgresUrl("postgresql://a:b@c:5432/d"));
        assertTrue(DbEnvironmentPostProcessor.isPostgresUrl("jdbc:postgresql://c:5432/d"));
        assertFalse(DbEnvironmentPostProcessor.isPostgresUrl("mysql://a:b@c:3306/d"));
        assertTrue(DbEnvironmentPostProcessor.isMySqlUrl("mysql://a:b@c:3306/d"));
        assertFalse(DbEnvironmentPostProcessor.isMySqlUrl("postgres://a:b@c:5432/d"));
    }
}
