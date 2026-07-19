// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import javax.sql.DataSource;
import org.jdbi.v3.core.Jdbi;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers Postgres fixture — one container per JVM, reused across every integration
 * test class. Builds a HikariCP pool over the container's JDBC URL, a {@link Jdbi} over that pool,
 * and migrates the {@link JdbiVelocityBackend} schema once. {@link #truncate()} resets state
 * between tests so each starts from a clean store.
 */
final class PostgresSupport {

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("velocity")
          .withUsername("velocity")
          .withPassword("velocity-test")
          .withReuse(true);

  private static HikariDataSource dataSource;
  private static Jdbi jdbi;

  private PostgresSupport() {}

  /** Lazily starts the container, builds the pool + Jdbi, and migrates the schema once. */
  static synchronized Jdbi ready() {
    if (jdbi != null) {
      return jdbi;
    }
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    final HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(CONTAINER.getJdbcUrl());
    cfg.setUsername(CONTAINER.getUsername());
    cfg.setPassword(CONTAINER.getPassword());
    cfg.setMaximumPoolSize(8);
    dataSource = new HikariDataSource(cfg);
    jdbi = Jdbi.create(dataSource);
    // Migrate through the backend's own idempotent DDL path (Clock is irrelevant to migration).
    new JdbiVelocityBackend(jdbi, Clock.systemUTC()).migrate();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (dataSource != null) {
                    dataSource.close();
                  }
                }));
    return jdbi;
  }

  /** The pooled data source, exposed so a test can drive the {@link DataSource} constructor. */
  static DataSource dataSource() {
    ready();
    return dataSource;
  }

  /** Truncates every velocity table so a test starts from an empty store. */
  static void truncate() {
    ready();
    jdbi.useHandle(
        handle ->
            handle.execute(
                "TRUNCATE TABLE velocity_counts, velocity_sums, velocity_distinct_members"));
  }
}
