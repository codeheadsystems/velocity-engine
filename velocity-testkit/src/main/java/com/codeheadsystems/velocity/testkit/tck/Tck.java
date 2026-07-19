// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.testkit.tck;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeheadsystems.velocity.spi.model.Aggregation;
import com.codeheadsystems.velocity.spi.model.ApplyContext;
import com.codeheadsystems.velocity.spi.model.DistinctMember;
import com.codeheadsystems.velocity.spi.model.Feature;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.FeatureValue;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.QueryContext;
import com.codeheadsystems.velocity.spi.model.QueryTuple;
import com.codeheadsystems.velocity.spi.model.Subject;
import com.codeheadsystems.velocity.spi.model.Window;
import com.codeheadsystems.velocity.spi.model.WindowType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Shared fixtures and assertion helpers for the conformance scenarios. */
final class Tck {

  static final Namespace NS_A = new Namespace("tck-ns-a");
  static final Namespace NS_B = new Namespace("tck-ns-b");
  static final Subject SUBJECT_A = new Subject("card", "subject-a");
  static final Subject SUBJECT_B = new Subject("card", "subject-b");

  static final Window SLIDING_5S = new Window(Duration.ofSeconds(5), WindowType.SLIDING);
  static final Window SLIDING_1M = new Window(Duration.ofMinutes(1), WindowType.SLIDING);
  static final Window TUMBLING_1M = new Window(Duration.ofMinutes(1), WindowType.TUMBLING);
  static final Window TUMBLING_1H = new Window(Duration.ofHours(1), WindowType.TUMBLING);

  /** A window no reference-backend window spec covers — used to drive the negative paths. */
  static final Window UNSUPPORTED = new Window(Duration.ofDays(7), WindowType.SLIDING);

  static final String DIMENSION = "ip";

  private Tck() {}

  static Feature countFeature(final Window... windows) {
    return new Feature("tck.count", Aggregation.count(), List.of(windows));
  }

  static Feature sumFeature(final Window... windows) {
    return new Feature("tck.sum", Aggregation.sum(), List.of(windows));
  }

  static Feature distinctFeature(final Window... windows) {
    return new Feature("tck.distinct", Aggregation.distinct(DIMENSION), List.of(windows));
  }

  static ApplyContext apply(final Namespace namespace) {
    return new ApplyContext(namespace, null);
  }

  static QueryContext query(final Namespace namespace) {
    return new QueryContext(namespace, null);
  }

  static QueryTuple tuple(
      final Subject subject, final Aggregation aggregation, final Window window) {
    return new QueryTuple(subject, aggregation, window);
  }

  static DistinctMember member(final String token) {
    return new DistinctMember(token.getBytes(StandardCharsets.UTF_8));
  }

  static BigDecimal cents(final long amount) {
    return BigDecimal.valueOf(amount);
  }

  /** Asserts the result is a {@link FeatureResult.Success} and returns its value. */
  static FeatureValue successValue(final FeatureResult result) {
    assertThat(result).isInstanceOf(FeatureResult.Success.class);
    return ((FeatureResult.Success) result).value();
  }

  /** Asserts the result is a success whose numeric value equals {@code expected}. */
  static void assertValue(final FeatureResult result, final long expected) {
    assertThat(successValue(result).value()).isEqualByComparingTo(BigDecimal.valueOf(expected));
  }

  /** Asserts the result is a {@link FeatureResult.Failure} carrying {@code code}. */
  static FeatureResult.Failure assertFailure(
      final FeatureResult result, final com.codeheadsystems.velocity.spi.model.FailureCode code) {
    assertThat(result).isInstanceOf(FeatureResult.Failure.class);
    final FeatureResult.Failure failure = (FeatureResult.Failure) result;
    assertThat(failure.code()).isEqualTo(code);
    return failure;
  }
}
