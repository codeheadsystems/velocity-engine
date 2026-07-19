// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard;

import com.codeheadsystems.velocity.core.VelocityEngine;
import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.VelocityBackend;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import com.codeheadsystems.velocity.testkit.InMemoryVelocityBackend;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.time.Duration;
import java.util.List;

/**
 * Test application: wires the in-memory backend (fixed clock, so record and query share a window)
 * and seeds two feature definitions in the {@code acme} namespace — {@code card.count} and {@code
 * card.sum} over a 1-minute sliding window on the {@code default} backend.
 */
public final class TestApplication extends VelocityApplication {

  private static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);

  @Override
  protected VelocityBackend createBackend(final VelocityConfiguration configuration) {
    return new InMemoryVelocityBackend(MutableClock.atNow());
  }

  @Override
  protected void configureEngine(
      final VelocityEngine engine, final VelocityConfiguration configuration) {
    engine.reload(
        new Namespace("acme"),
        List.of(
            FeatureDefinition.count("card.count", "card", "default", List.of(SLIDING_1M)),
            FeatureDefinition.sum("card.sum", "card", "default", List.of(SLIDING_1M))));
  }
}
