// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AggregationTest {

  @Test
  void countFactoryHasNoDimension() {
    Aggregation aggregation = Aggregation.count();
    assertThat(aggregation.type()).isEqualTo(AggregationType.COUNT);
    assertThat(aggregation.dimension()).isNull();
  }

  @Test
  void sumFactoryHasNoDimension() {
    Aggregation aggregation = Aggregation.sum();
    assertThat(aggregation.type()).isEqualTo(AggregationType.SUM);
    assertThat(aggregation.dimension()).isNull();
  }

  @Test
  void distinctFactoryCarriesDimension() {
    Aggregation aggregation = Aggregation.distinct("ip");
    assertThat(aggregation.type()).isEqualTo(AggregationType.DISTINCT);
    assertThat(aggregation.dimension()).isEqualTo("ip");
  }

  @Test
  void distinctRequiresNonNullDimension() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Aggregation(AggregationType.DISTINCT, null));
  }

  @Test
  void distinctRejectsBlankDimension() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Aggregation(AggregationType.DISTINCT, "  "));
  }

  @Test
  void countRejectsNonNullDimension() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Aggregation(AggregationType.COUNT, "ip"));
  }

  @Test
  void sumRejectsNonNullDimension() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Aggregation(AggregationType.SUM, "amount"));
  }

  @Test
  void rejectsNullType() {
    assertThatNullPointerException().isThrownBy(() -> new Aggregation(null, null));
  }
}
