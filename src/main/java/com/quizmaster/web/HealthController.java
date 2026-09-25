package com.quizmaster.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health endpoint for Railway (and any other host that probes a container
 * before sending traffic to it). It also reports the database it reached, which
 * is the first thing to check when a deployment misbehaves.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    @Value("${spring.datasource.url:unknown}")
    private String datasourceUrl;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("application", "QuizMaster Web");
        body.put("status", "UP");
        body.put("database", describeDatabase());
        try {
            Integer one = jdbcTemplate.queryForObject("select 1", Integer.class);
            body.put("databaseReachable", one != null && one == 1);
        } catch (RuntimeException ex) {
            body.put("status", "DEGRADED");
            body.put("databaseReachable", false);
            body.put("databaseError", ex.getMessage());
            return ResponseEntity.status(503).body(body);
        }
        return ResponseEntity.ok(body);
    }

    /** The JDBC URL without credentials, so the health output is safe to share. */
    private String describeDatabase() {
        String url = datasourceUrl == null ? "unknown" : datasourceUrl;
        if (url.startsWith("jdbc:postgresql")) {
            return "PostgreSQL";
        }
        if (url.startsWith("jdbc:mysql")) {
            return "MySQL/MariaDB";
        }
        if (url.startsWith("jdbc:h2")) {
            return "H2 (file database - no Postgres/MySQL configured)";
        }
        return url;
    }
}
