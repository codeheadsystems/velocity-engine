// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.redis;

import com.codeheadsystems.velocity.testkit.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Base for the real-Redis integration tests: a fresh {@link RedisVelocityBackend} over the shared
 * Testcontainers Redis, driven by a controllable {@link MutableClock}, with the store flushed
 * before each test. Disabled where Docker is absent via {@code VELOCITY_SKIP_TESTCONTAINERS=1}.
 */
@DisabledIfEnvironmentVariable(named = "VELOCITY_SKIP_TESTCONTAINERS", matches = "1")
abstract class AbstractRedisBackendTest {

  protected MutableClock clock;
  protected RedisVelocityBackend backend;

  @BeforeEach
  void setUpBackend() {
    RedisSupport.flush();
    clock = MutableClock.atNow();
    backend = new RedisVelocityBackend(RedisSupport.connection(), clock);
  }
}
