package com.quizmaster.db;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves the database connection before Spring Boot auto-configuration runs,
 * so the same jar works on Railway (Postgres plugin, MySQL plugin, or plain
 * variables), on a laptop with no database at all, and anywhere else.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code SPRING_DATASOURCE_URL} – the operator configured Spring directly, hands off.</li>
 *   <li>{@code DATABASE_URL} / {@code POSTGRES_URL} / {@code DB_URL} – a
 *       {@code postgres://} or {@code mysql://} URL, as Railway's plugins provide.</li>
 *   <li>Railway MySQL plugin variables: {@code MYSQL_URL}, or
 *       {@code MYSQLHOST}/{@code MYSQLPORT}/{@code MYSQLUSER}/{@code MYSQLPASSWORD}/{@code MYSQLDATABASE}.</li>
 *   <li>Generic {@code DB_HOST}/{@code DB_PORT}/{@code DB_NAME}/{@code DB_USER}/{@code DB_PASSWORD}.</li>
 *   <li>Nothing found: an embedded H2 file database, so the app still starts.</li>
 * </ol>
 *
 * <p>The URL handling lives in small static methods so it can be unit tested
 * without a database.
 */
public class DbEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String H2_DRIVER = "org.h2.Driver";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // An explicit datasource wins: SPRING_DATASOURCE_URL, spring.datasource.url
        // in a properties file, or a test's @TestPropertySource. Nothing to resolve.
        if (hasText(environment.getProperty("spring.datasource.url"))
                || hasText(environment.getProperty("SPRING_DATASOURCE_URL"))) {
            return;
        }

        Map<String, Object> resolved = resolve(environment);
        if (!resolved.isEmpty()) {
            // addFirst so these beat values from any lower-precedence source.
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("quizmasterDatasource", resolved));
            // Plain stdout: this runs before Boot initialises logging, so a
            // logger here would swallow the message.
            System.out.println("[quizmaster] database -> " + resolved.get("spring.datasource.url"));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Builds the {@code spring.datasource.*} properties for the detected environment. */
    static Map<String, Object> resolve(ConfigurableEnvironment environment) {
        Map<String, Object> properties = new LinkedHashMap<>();

        String url = firstText(environment,
                "DATABASE_URL", "POSTGRES_URL", "POSTGRESQL_URL", "MYSQL_URL",
                "DB_URL", "QUIZMASTER_DB_URL");

        if (hasText(url) && isPostgresUrl(url)) {
            applyUrl(properties, PostgresUrl.parse(url), POSTGRES_DRIVER);
            return properties;
        }
        if (hasText(url) && isMySqlUrl(url)) {
            applyUrl(properties, MySqlUrl.parse(url), MYSQL_DRIVER);
            return properties;
        }

        // Railway's MySQL plugin also exports the pieces separately.
        String mysqlHost = firstText(environment, "MYSQLHOST", "MYSQL_HOST", "DB_HOST");
        if (hasText(mysqlHost)) {
            String host = mysqlHost;
            String port = firstTextOr(environment, "3306", "MYSQLPORT", "MYSQL_PORT", "DB_PORT");
            String database = firstTextOr(environment, "quizmaster",
                    "MYSQLDATABASE", "MYSQL_DATABASE", "MYSQL_DB", "DB_NAME");
            String user = firstTextOr(environment, "root",
                    "MYSQLUSER", "MYSQL_USER", "DB_USER", "DB_USERNAME");
            String password = firstTextOr(environment, "",
                    "MYSQLPASSWORD", "MYSQL_PASSWORD", "MYSQL_ROOT_PASSWORD", "DB_PASSWORD");
            properties.put("spring.datasource.url", mysqlJdbcUrl(host, port, database, null));
            properties.put("spring.datasource.username", user);
            properties.put("spring.datasource.password", password);
            properties.put("spring.datasource.driver-class-name", MYSQL_DRIVER);
            return properties;
        }

        String pgHost = firstText(environment, "PGHOST", "POSTGRES_HOST");
        if (hasText(pgHost)) {
            String host = pgHost;
            String port = firstTextOr(environment, "5432", "PGPORT", "POSTGRES_PORT");
            String database = firstTextOr(environment, "quizmaster", "PGDATABASE", "POSTGRES_DB");
            String user = firstTextOr(environment, "postgres", "PGUSER", "POSTGRES_USER");
            String password = firstTextOr(environment, "", "PGPASSWORD", "POSTGRES_PASSWORD");
            properties.put("spring.datasource.url", postgresJdbcUrl(host, port, database, null));
            properties.put("spring.datasource.username", user);
            properties.put("spring.datasource.password", password);
            properties.put("spring.datasource.driver-class-name", POSTGRES_DRIVER);
            return properties;
        }

        // No database configured: H2 file database next to the app so the very
        // first run works with zero setup.
        String dataDir = firstTextOr(environment, "./data", "QUIZMASTER_DATA_DIR");
        properties.put("spring.datasource.url",
                "jdbc:h2:file:" + trimTrailingSlash(dataDir) + "/quizmaster;DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE");
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("spring.datasource.driver-class-name", H2_DRIVER);
        return properties;
    }

    private static void applyUrl(Map<String, Object> properties, ParsedUrl parsed, String driver) {
        properties.put("spring.datasource.url", parsed.jdbcUrl());
        properties.put("spring.datasource.username", parsed.username());
        properties.put("spring.datasource.password", parsed.password());
        properties.put("spring.datasource.driver-class-name", driver);
    }

    /** Result of parsing a {@code postgres://} / {@code mysql://} connection string. */
    public record ParsedUrl(String jdbcUrl, String username, String password) {
    }

    /** Parses a {@code postgres://} / {@code postgresql://} URL into a JDBC URL. */
    public static final class PostgresUrl {
        private PostgresUrl() {
        }

        public static ParsedUrl parse(String raw) {
            URI uri = URI.create(raw.trim());
            Credentials credentials = Credentials.from(uri);
            String database = databaseOf(uri, "quizmaster");
            return new ParsedUrl(postgresJdbcUrl(uri.getHost(), String.valueOf(portOf(uri, 5432)), database, uri.getQuery()),
                    credentials.username(), credentials.password());
        }
    }

    /** Parses a {@code mysql://} URL into a JDBC URL. */
    public static final class MySqlUrl {
        private MySqlUrl() {
        }

        public static ParsedUrl parse(String raw) {
            URI uri = URI.create(raw.trim());
            Credentials credentials = Credentials.from(uri);
            String database = databaseOf(uri, "quizmaster");
            return new ParsedUrl(mysqlJdbcUrl(uri.getHost(), String.valueOf(portOf(uri, 3306)), database, uri.getQuery()),
                    credentials.username(), credentials.password());
        }
    }

    public static boolean isPostgresUrl(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT).trim();
        return lower.startsWith("postgres://") || lower.startsWith("postgresql://")
                || lower.startsWith("jdbc:postgresql:");
    }

    public static boolean isMySqlUrl(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT).trim();
        return lower.startsWith("mysql://") || lower.startsWith("jdbc:mysql:");
    }

    /**
     * Builds the Postgres JDBC URL. Any query parameters already present are
     * kept; otherwise {@code sslmode=prefer} is added, which works both on
     * Railway's private network (no TLS) and through the public proxy (TLS).
     */
    public static String postgresJdbcUrl(String host, String port, String database, String query) {
        StringBuilder sb = new StringBuilder("jdbc:postgresql://")
                .append(host).append(':').append(port).append('/').append(database);
        if (hasText(query)) {
            sb.append('?').append(query);
        } else {
            sb.append("?sslmode=prefer");
        }
        return sb.toString();
    }

    /**
     * Builds the MySQL JDBC URL. utf8mb4 negotiation is required for the Urdu
     * (RTL) question text the app supports.
     */
    public static String mysqlJdbcUrl(String host, String port, String database, String query) {
        StringBuilder sb = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/').append(database);
        if (hasText(query)) {
            // Railway hands over a ready-made query string; keep it as-is.
            sb.append('?').append(query);
        } else {
            sb.append("?useSSL=false")
                    .append("&allowPublicKeyRetrieval=true")
                    .append("&characterEncoding=UTF-8")
                    .append("&connectionCollation=utf8mb4_unicode_ci")
                    .append("&serverTimezone=UTC")
                    .append("&connectTimeout=10000");
        }
        return sb.toString();
    }

    private static String databaseOf(URI uri, String fallback) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return fallback;
        }
        String name = path.startsWith("/") ? path.substring(1) : path;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            name = name.substring(0, slash);
        }
        return name.isBlank() ? fallback : name;
    }

    private static int portOf(URI uri, int fallback) {
        return uri.getPort() > 0 ? uri.getPort() : fallback;
    }

    /** Reads {@code user:password} out of the URL's authority. */
    private record Credentials(String username, String password) {
        static Credentials from(URI uri) {
            String info = uri.getUserInfo();
            if (info == null || info.isBlank()) {
                return new Credentials("", "");
            }
            int colon = info.indexOf(':');
            if (colon < 0) {
                return new Credentials(decode(info), "");
            }
            return new Credentials(decode(info.substring(0, colon)), decode(info.substring(colon + 1)));
        }

        /** Percent-decodes a URL component without turning "+" into a space. */
        private static String decode(String value) {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        }
    }

    private static String firstText(ConfigurableEnvironment environment, String... keys) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String firstTextOr(ConfigurableEnvironment environment, String fallback, String... keys) {
        String value = firstText(environment, keys);
        return value != null ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "." : trimmed;
    }
}
