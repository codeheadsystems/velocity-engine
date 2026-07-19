// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.CountStore;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities.WindowSpec;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.spi.model.Subject;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * When a definition is bound to a backend that does not implement the required aggregation mix-in
 * (an inconsistent configuration the capability validator would normally catch), the engine emits a
 * distinguishable FAILED outcome rather than throwing.
 */
class UnsupportedMixinTest {

  private static final Namespace ACME = new Namespace("acme");
  private static final Subject CARD = new Subject("card", "1234");

  @Test
  void sumIntentToCountOnlyBackendFailsGracefully() {
    final BackendRegistry registry = new BackendRegistry(Map.of("fake", new CountOnlyBackend()));
    final MutableFeatureDefinitionProvider provider = new MutableFeatureDefinitionProvider();
    // Install directly (bypassing engine.reload's validation) to reach the defensive path.
    provider.reload(
        ACME,
        List.of(FeatureDefinition.sum("card.sum", "card", "fake", List.of(Fixtures.TUMBLING_1H))));
    final VelocityEngine engine =
        new VelocityEngine(
            registry,
            provider,
            new DimensionHasher(new InMemoryNamespaceSaltProvider("m".getBytes(UTF_8))),
            new CapabilityValidator());

    final ApplyResult result = engine.record(ACME, CARD, Map.of(), new BigDecimal("100"));
    assertThat(result.perFeature())
        .allSatisfy(pf -> assertThat(pf.status()).isEqualTo(ApplyStatus.FAILED))
        .allSatisfy(pf -> assertThat(pf.result()).isInstanceOf(FeatureResult.Failure.class));

    // The query path is symmetric: a Failure per requested window, never a thrown exception.
    assertThat(engine.query(ACME, CARD, "card.sum"))
        .allSatisfy(r -> assertThat(r).isInstanceOf(FeatureResult.Failure.class));
  }

  /** A backend that only implements COUNT, used to exercise the engine's missing-mix-in path. */
  private static final class CountOnlyBackend implements CountStore {

    @Override
    public BackendCapabilities capabilities() {
      return new BackendCapabilities(
          Set.of(com.codeheadsystems.velocity.spi.model.AggregationType.COUNT),
          List.of(new WindowSpec(Fixtures.TUMBLING_1H, Exactness.EXACT, Duration.ofHours(1))),
          false,
          Long.MAX_VALUE,
          Long.MAX_VALUE,
          Duration.ofDays(30),
          ReadYourWriteLevel.ATOMIC,
          false,
          false,
          Integer.MAX_VALUE);
    }

    @Override
    public ApplyResult applyCount(final ApplyContext ctx, final List<CountIntent> intents) {
      return new ApplyResult(List.of());
    }

    @Override
    public List<FeatureResult> queryCount(final QueryContext ctx, final List<QueryTuple> tuples) {
      return List.of();
    }

    @Override
    public void purge(final Namespace namespace, final @Nullable Subject subject) {
      // no-op
    }
  }
}
