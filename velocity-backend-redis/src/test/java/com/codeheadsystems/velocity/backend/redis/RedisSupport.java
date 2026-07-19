// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.backend.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers Redis fixture — one container per JVM, reused across every integration test
 * class. Exposes a single Lettuce connection to the mapped port; {@link #flush()} resets state
 * between tests so each starts from an empty Redis.
 */
final class RedisSupport {

  @SuppressWarnings("resource") // container + client are closed by the JVM shutdown hook
  private static final GenericContainer<?> CONTAINER =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private static RedisClient client;
  private static StatefulRedisConnection<String, String> connection;

  private RedisSupport() {}

  /** Lazily starts the container and opens a reusable Lettuce connection. */
  static synchronized StatefulRedisConnection<String, String> connection() {
    if (connection != null) {
      return connection;
    }
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    client =
        RedisClient.create(RedisURI.create(CONTAINER.getHost(), CONTAINER.getMappedPort(6379)));
    connection = client.connect();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (connection != null) {
                    connection.close();
                  }
                  if (client != null) {
                    client.shutdown();
                  }
                }));
    return connection;
  }

  /** Flushes every key so a test starts from an empty store. */
  static void flush() {
    connection().sync().flushall();
  }
}
