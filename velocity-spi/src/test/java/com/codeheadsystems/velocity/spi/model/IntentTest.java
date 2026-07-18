// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.codeheadsystems.velocity.spi.model.Intent.CountIntent;
import com.codeheadsystems.velocity.spi.model.Intent.DistinctIntent;
import com.codeheadsystems.velocity.spi.model.Intent.SumIntent;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntentTest {

  private static final Window HOUR = new Window(Duration.ofHours(1), WindowType.TUMBLING);
  private static final Subject SUBJECT = new Subject("card", "abc");

  private static Feature feature(Aggregation aggregation) {
    return new Feature("f", aggregation, List.of(HOUR));
  }

  @Test
  void countIntentRequiresCountFeature() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CountIntent(feature(Aggregation.sum()), SUBJECT));
  }

  @Test
  void countIntentAcceptsCountFeature() {
    CountIntent intent = new CountIntent(feature(Aggregation.count()), SUBJECT);
    assertThat(intent.subject()).isEqualTo(SUBJECT);
    assertThat(intent.feature().aggregation().type()).isEqualTo(AggregationType.COUNT);
  }

  @Test
  void sumIntentRequiresSumFeature() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new SumIntent(feature(Aggregation.count()), SUBJECT, new BigDecimal("100")));
  }

  @Test
  void sumIntentRejectsNonZeroScale() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new SumIntent(feature(Aggregation.sum()), SUBJECT, new BigDecimal("1.50")));
  }

  @Test
  void sumIntentAcceptsIntegerCentsIncludingNegative() {
    SumIntent refund = new SumIntent(feature(Aggregation.sum()), SUBJECT, new BigDecimal("-500"));
    assertThat(refund.valueCents()).isEqualByComparingTo("-500");
  }

  @Test
  void distinctIntentRequiresDistinctFeature() {
    DistinctMember member = new DistinctMember(new byte[] {1, 2});
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DistinctIntent(feature(Aggregation.count()), SUBJECT, member));
  }

  @Test
  void distinctIntentAcceptsDistinctFeature() {
    DistinctMember member = new DistinctMember(new byte[] {1, 2});
    DistinctIntent intent =
        new DistinctIntent(feature(Aggregation.distinct("ip")), SUBJECT, member);
    assertThat(intent.member()).isEqualTo(member);
  }

  @Test
  void rejectsNullComponents() {
    assertThatNullPointerException()
        .isThrownBy(() -> new CountIntent(feature(Aggregation.count()), null));
    assertThatNullPointerException()
        .isThrownBy(() -> new SumIntent(feature(Aggregation.sum()), SUBJECT, null));
    assertThatNullPointerException()
        .isThrownBy(() -> new DistinctIntent(feature(Aggregation.distinct("ip")), SUBJECT, null));
  }

  @Test
  void variantsShareTheSealedIntentType() {
    Intent intent = new CountIntent(feature(Aggregation.count()), SUBJECT);
    assertThat(intent).isInstanceOf(Intent.class);
    assertThat(intent.feature().name()).isEqualTo("f");
  }
}
