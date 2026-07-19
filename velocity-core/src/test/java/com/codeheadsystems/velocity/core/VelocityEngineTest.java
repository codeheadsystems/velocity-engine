// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.model.ApplyResult;
import com.codeheadsystems.velocity.spi.model.ApplyStatus;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.PerFeature;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.testkit.InMemoryVelocityBackend;
import com.codeheadsystems.velocity.testkit.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests driving the real in-memory backend through the engine. */
class VelocityEngineTest {

  private static final Namespace ACME = new Namespace("acme");
  private static final Subject CARD = new Subject("card", "1234");
  private static final Map<String, String> DIMENSIONS =
      Map.of("ip", "203.0.113.7", "merchant", "m-88");
  private static final BigDecimal VALUE = new BigDecimal("14950");

  private MutableClock clock;
  private InMemoryVelocityBackend backend;
  private MutableFeatureDefinitionProvider provider;
  private VelocityEngine engine;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(Instant.parse("2026-03-15T12:30:30Z"));
    backend = new InMemoryVelocityBackend(clock);
    provider = new MutableFeatureDefinitionProvider();
    engine = Fixtures.engine(provider, backend);
    engine.reload(ACME, Fixtures.allDefinitions());
  }

  @Test
  void fanOut_updatesEveryMatchedFeatureAcrossSubjects() {
    engine.record(ACME, CARD, DIMENSIONS, VALUE);

    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
    assertThat(value(engine.query(ACME, CARD, "card.sum"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("14950");
    assertThat(value(engine.query(ACME, CARD, "card.distinct.ip"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
    // The ip.count feature fanned out to a *different* subject derived from the ip dimension.
    final Subject ipSubject = new Subject("ip", "203.0.113.7");
    assertThat(value(engine.query(ACME, ipSubject, "ip.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
  }

  @Test
  void record_resultReflectsTheWrite_readYourWrite() {
    final ApplyResult result = engine.record(ACME, CARD, DIMENSIONS, VALUE);

    final PerFeature cardCount1h =
        result.perFeature().stream()
            .filter(
                pf ->
                    pf.feature().name().equals("card.count")
                        && pf.result() instanceof FeatureResult.Success success
                        && success.value().window().equals(Fixtures.TUMBLING_1H))
            .findFirst()
            .orElseThrow();
    assertThat(cardCount1h.status()).isEqualTo(ApplyStatus.APPLIED);
    final FeatureValue value = ((FeatureResult.Success) cardCount1h.result()).value();
    assertThat(value.value()).isEqualByComparingTo("1");
    // FR-40: every returned value is stamped with the definition version it was computed under.
    assertThat(value.definitionVersionHash())
        .isEqualTo(provider.definitions(ACME).versionHash())
        .isNotNull();
  }

  @Test
  void record_noMatchingDefinition_isEmptyNotError() {
    // A subject whose type matches no definition and no dimension-derived fan-out applies.
    final ApplyResult result = engine.record(ACME, new Subject("device", "d-1"), Map.of(), null);
    assertThat(result.perFeature()).isEmpty();
  }

  @Test
  void record_sumWithoutValue_isSkippedWithReason() {
    final ApplyResult result = engine.record(ACME, CARD, DIMENSIONS, null);

    final PerFeature sum =
        result.perFeature().stream()
            .filter(pf -> pf.feature().name().equals("card.sum"))
            .findFirst()
            .orElseThrow();
    assertThat(sum.status()).isEqualTo(ApplyStatus.SKIPPED);
    assertThat(sum.result()).isInstanceOf(FeatureResult.Failure.class);
    // COUNT still applied even though SUM was skipped.
    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
  }

  @Test
  void record_distinctWithoutDimension_isSkippedWithReason() {
    // The card.distinct.ip feature resolves its (primary card) subject, but the "ip" dimension it
    // counts distinctly is absent, so no member can be hashed and the write is skipped — the
    // DISTINCT twin of record_sumWithoutValue_isSkippedWithReason (FR-18 fan-out skip path).
    final ApplyResult result = engine.record(ACME, CARD, Map.of(), null);

    final PerFeature distinct =
        result.perFeature().stream()
            .filter(pf -> pf.feature().name().equals("card.distinct.ip"))
            .findFirst()
            .orElseThrow();
    assertThat(distinct.status()).isEqualTo(ApplyStatus.SKIPPED);
    assertThat(distinct.result()).isInstanceOf(FeatureResult.Failure.class);
    // COUNT still applied even though DISTINCT was skipped.
    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
  }

  @Test
  void distinct_countsDistinctHashedValues() {
    engine.record(ACME, CARD, Map.of("ip", "1.1.1.1"), null);
    engine.record(ACME, CARD, Map.of("ip", "2.2.2.2"), null);
    assertThat(value(engine.query(ACME, CARD, "card.distinct.ip"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("2");

    // Recording the same ip again does not raise cardinality.
    engine.record(ACME, CARD, Map.of("ip", "1.1.1.1"), null);
    assertThat(value(engine.query(ACME, CARD, "card.distinct.ip"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("2");
  }

  @Test
  void namespaces_areIsolated() {
    final Namespace beta = new Namespace("beta");
    engine.reload(beta, Fixtures.allDefinitions());

    engine.record(ACME, CARD, DIMENSIONS, VALUE);
    engine.record(ACME, CARD, DIMENSIONS, VALUE);
    engine.record(beta, CARD, DIMENSIONS, VALUE);

    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("2");
    assertThat(value(engine.query(beta, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
  }

  @Test
  void reload_isAtomicAndChangesVersionAndResolvableFeatures() {
    final String before = provider.definitions(ACME).versionHash();

    // Swap: keep card.count, drop card.sum, keep the distinct + ip features.
    engine.reload(
        ACME, List.of(Fixtures.cardCount(), Fixtures.cardDistinctIp(), Fixtures.ipCount()));
    final String after = provider.definitions(ACME).versionHash();
    assertThat(after).isNotEqualTo(before);

    // The removed feature no longer resolves.
    assertThatThrownBy(() -> engine.query(ACME, CARD, "card.sum"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.sum");

    // A retained feature still works.
    engine.record(ACME, CARD, DIMENSIONS, VALUE);
    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("1");
  }

  @Test
  void reload_rejectsInvalidDefinitionSet_andDoesNotApply() {
    final String before = provider.definitions(ACME).versionHash();
    final var badWindow =
        FeatureDefinition.count(
            "card.count.1d",
            "card",
            Fixtures.BACKEND,
            List.of(
                new com.codeheadsystems.velocity.spi.model.Window(
                    java.time.Duration.ofDays(1),
                    com.codeheadsystems.velocity.spi.model.WindowType.TUMBLING)));

    assertThatThrownBy(() -> engine.reload(ACME, List.of(Fixtures.cardCount(), badWindow)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("card.count.1d");

    // Rejected atomically: the previous snapshot is untouched.
    assertThat(provider.definitions(ACME).versionHash()).isEqualTo(before);
  }

  @Test
  void capabilities_isPassthrough() {
    assertThat(engine.capabilities(Fixtures.BACKEND)).isSameAs(backend.capabilities());
  }

  @Test
  void purge_erasesAcrossBackends() {
    engine.record(ACME, CARD, DIMENSIONS, VALUE);
    engine.purge(ACME, CARD);
    assertThat(value(engine.query(ACME, CARD, "card.count"), Fixtures.TUMBLING_1H))
        .isEqualByComparingTo("0");
  }

  @Test
  void query_batchReturnsPerFeatureResults() {
    engine.record(ACME, CARD, DIMENSIONS, VALUE);
    final Map<String, List<FeatureResult>> results =
        engine.query(ACME, CARD, List.of("card.count", "card.sum"));
    assertThat(results).containsOnlyKeys("card.count", "card.sum");
    assertThat(value(results.get("card.sum"), Fixtures.TUMBLING_1H)).isEqualByComparingTo("14950");
  }

  private static BigDecimal value(
      final List<FeatureResult> results,
      final com.codeheadsystems.velocity.spi.model.Window window) {
    return results.stream()
        .filter(r -> r instanceof FeatureResult.Success s && s.value().window().equals(window))
        .map(r -> ((FeatureResult.Success) r).value().value())
        .findFirst()
        .orElseThrow(() -> new AssertionError("no success result for window " + window));
  }
}
