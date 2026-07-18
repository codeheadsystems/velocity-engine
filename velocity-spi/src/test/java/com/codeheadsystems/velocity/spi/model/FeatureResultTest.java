// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureResultTest {

  private static FeatureValue value() {
    Window window = new Window(Duration.ofHours(1), WindowType.TUMBLING);
    Feature feature = new Feature("card.count.1h", Aggregation.count(), List.of(window));
    Instant now = Instant.parse("2026-07-18T01:00:00Z");
    return new FeatureValue(
        feature,
        window,
        new BigDecimal("7"),
        Exactness.EXACT,
        ReadYourWriteLevel.ATOMIC,
        null,
        new WindowBounds(now.minus(Duration.ofHours(1)), now),
        now);
  }

  @Test
  void successFactoryProducesSuccess() {
    FeatureResult result = FeatureResult.success(value());
    assertThat(result.isSuccess()).isTrue();
    assertThat(result).isInstanceOf(FeatureResult.Success.class);
  }

  @Test
  void failureFactoryProducesFailure() {
    FeatureResult result = FeatureResult.failure(FailureCode.UNAVAILABLE, "backend down");
    assertThat(result.isSuccess()).isFalse();
    assertThat(result).isInstanceOf(FeatureResult.Failure.class);
  }

  @Test
  void failureDetailIsOptional() {
    FeatureResult result = FeatureResult.failure(FailureCode.DEADLINE_EXCEEDED, null);
    assertThat(((FeatureResult.Failure) result).detail()).isNull();
  }

  @Test
  void successRejectsNullValue() {
    assertThatNullPointerException().isThrownBy(() -> FeatureResult.success(null));
  }

  @Test
  void failureRejectsNullCode() {
    assertThatNullPointerException().isThrownBy(() -> FeatureResult.failure(null, "x"));
  }

  @Test
  void patternMatchExtractsBranchData() {
    FeatureResult success = FeatureResult.success(value());
    FeatureResult failure = FeatureResult.failure(FailureCode.CARDINALITY_CAP_EXCEEDED, "capped");

    assertThat(describe(success)).isEqualTo("value=7");
    assertThat(describe(failure)).isEqualTo("fail=CARDINALITY_CAP_EXCEEDED");
  }

  private static String describe(FeatureResult result) {
    return switch (result) {
      case FeatureResult.Success s -> "value=" + s.value().value().toPlainString();
      case FeatureResult.Failure f -> "fail=" + f.code();
    };
  }
}
