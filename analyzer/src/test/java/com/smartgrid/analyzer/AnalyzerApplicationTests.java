package com.smartgrid.analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@code @Sql} sets up the schema so the eagerly-firing {@code AnomalyScanner}
 * (its {@code @Scheduled} method runs shortly after context startup) hits real
 * tables instead of failing with "relation does not exist" — Spring AI's
 * Ollama autoconfiguration itself doesn't contact a live server at startup, so
 * this test needs no running Ollama.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql("/schema.sql")
class AnalyzerApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Test
	void contextLoads() {
	}

}
