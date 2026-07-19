// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.core.model.CapabilityValidationResult;
import com.codeheadsystems.velocity.core.model.FeatureDefinition;
import com.codeheadsystems.velocity.spi.model.AggregationType;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.BackendCapabilities.WindowSpec;
import com.codeheadsystems.velocity.spi.model.Exactness;
import com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapabilityValidatorTest {

  private final CapabilityValidator validator = new CapabilityValidator();

  private static BackendCapabilities caps(
      final Set<AggregationType> aggregations,
      final List<Window> windows,
      final Duration maxRetention) {
    return new BackendCapabilities(
        aggregations,
        windows.stream().map(w -> new WindowSpec(w, Exactness.EXACT, w.duration())).toList(),
        false,
        Long.MAX_VALUE,
        Long.MAX_VALUE,
        maxRetention,
        ReadYourWriteLevel.ATOMIC,
        false,
        false,
        Integer.MAX_VALUE);
  }

  @Test
  void validDefinitionPasses() {
    final CapabilityValidationResult result =
        validator.validate(
            Fixtures.cardCount(),
            caps(
                Set.of(AggregationType.COUNT),
                List.of(Fixtures.TUMBLING_1M, Fixtures.TUMBLING_1H),
                Duration.ofDays(30)));
    assertThat(result.valid()).isTrue();
    assertThat(result.violations()).isEmpty();
  }

  @Test
  void unsupportedWindowIsRejected() {
    final CapabilityValidationResult result =
        validator.validate(
            Fixtures.cardCount(),
            caps(
                Set.of(AggregationType.COUNT), List.of(Fixtures.TUMBLING_1M), Duration.ofDays(30)));
    assertThat(result.valid()).isFalse();
    assertThat(result.violations())
        .anySatisfy(v -> assertThat(v.message()).contains("PT1H").contains("not supported"));
  }

  @Test
  void unsupportedAggregationIsRejected() {
    final CapabilityValidationResult result =
        validator.validate(
            Fixtures.cardSum(),
            caps(
                Set.of(AggregationType.COUNT),
                List.of(Fixtures.TUMBLING_1M, Fixtures.TUMBLING_1H),
                Duration.ofDays(30)));
    assertThat(result.violations())
        .anySatisfy(v -> assertThat(v.message()).contains("SUM").contains("not supported"));
  }

  @Test
  void retentionTooShortIsRejected() {
    final FeatureDefinition oneHour =
        FeatureDefinition.count(
            "card.count.1h", "card", Fixtures.BACKEND, List.of(Fixtures.TUMBLING_1H));
    final CapabilityValidationResult result =
        validator.validate(
            oneHour,
            caps(
                Set.of(AggregationType.COUNT),
                List.of(Fixtures.TUMBLING_1H),
                Duration.ofMinutes(30)));
    assertThat(result.violations())
        .anySatisfy(v -> assertThat(v.message()).contains("maxRetention").contains("FR-22a"));
  }

  @Test
  void distinctOnCountOnlyBackendIsRejected() {
    final CapabilityValidationResult result =
        validator.validate(
            Fixtures.cardDistinctIp(),
            caps(
                Set.of(AggregationType.COUNT), List.of(Fixtures.TUMBLING_1H), Duration.ofDays(30)));
    assertThat(result.violations()).anySatisfy(v -> assertThat(v.message()).contains("DISTINCT"));
  }

  @Test
  void collectsAllViolationsNotJustTheFirst() {
    // Wrong aggregation AND an unsupported window in the same definition.
    final CapabilityValidationResult result =
        validator.validate(
            Fixtures.cardSum(),
            caps(
                Set.of(AggregationType.COUNT),
                List.of(new Window(Duration.ofSeconds(5), WindowType.SLIDING)),
                Duration.ofDays(30)));
    assertThat(result.violations()).hasSizeGreaterThanOrEqualTo(2);
  }
}
