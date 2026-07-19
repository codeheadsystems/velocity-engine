// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Shared test fixtures for the engine tests: standard windows, the canonical definition set. */
final class Fixtures {

  static final String BACKEND = "memory";
  static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);
  static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  private Fixtures() {}

  static FeatureDefinition cardCount() {
    return FeatureDefinition.count(
        "card.count", "card", BACKEND, List.of(TUMBLING_1M, TUMBLING_1H));
  }

  static FeatureDefinition cardSum() {
    return FeatureDefinition.sum("card.sum", "card", BACKEND, List.of(TUMBLING_1M, TUMBLING_1H));
  }

  static FeatureDefinition cardDistinctIp() {
    return FeatureDefinition.distinct(
        "card.distinct.ip", "card", "ip", BACKEND, List.of(TUMBLING_1H));
  }

  /** An {@code ip}-subject COUNT whose subject is derived from the event's {@code ip} dimension. */
  static FeatureDefinition ipCount() {
    return new FeatureDefinition(
        "ip.count",
        "ip",
        com.codeheadsystems.velocity.core.model.SubjectSource.fromDimension("ip"),
        com.codeheadsystems.velocity.spi.model.Aggregation.count(),
        List.of(TUMBLING_1M, TUMBLING_1H),
        BACKEND,
        null);
  }

  static List<FeatureDefinition> allDefinitions() {
    return List.of(cardCount(), cardSum(), cardDistinctIp(), ipCount());
  }

  static VelocityEngine engine(
      final MutableFeatureDefinitionProvider provider, final VelocityBackend backend) {
    final BackendRegistry registry = new BackendRegistry(Map.of(BACKEND, backend));
    final DimensionHasher hasher =
        new DimensionHasher(new InMemoryNamespaceSaltProvider("test-master".getBytes(UTF_8)));
    return new VelocityEngine(registry, provider, hasher, new CapabilityValidator());
  }
}
