// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.jdbi;

import com.codeheadsystems.velocity.testkit.MutableClock;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Base for the real-Postgres integration tests: a fresh {@link JdbiVelocityBackend} over the shared
 * Testcontainers Postgres, driven by a controllable {@link MutableClock}, with the store truncated
 * before each test. Disabled where Docker is absent via {@code VELOCITY_SKIP_TESTCONTAINERS=1}.
 */
@DisabledIfEnvironmentVariable(named = "VELOCITY_SKIP_TESTCONTAINERS", matches = "1")
abstract class AbstractJdbiBackendTest {

  protected MutableClock clock;
  protected JdbiVelocityBackend backend;

  @BeforeEach
  void setUpBackend() {
    final Jdbi jdbi = PostgresSupport.ready();
    PostgresSupport.truncate();
    clock = MutableClock.atNow();
    backend = new JdbiVelocityBackend(jdbi, clock);
  }
}
