// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.codeheadsystems.velocity.spi.model.SeedAggregate.ExactDistinct;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.HllDistinct;
import com.codeheadsystems.velocity.spi.model.SeedAggregate.SumValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeedAggregateTest {

  @Test
  void sumValueRejectsNonZeroScale() {
    assertThatIllegalArgumentException().isThrownBy(() -> new SumValue(new BigDecimal("1.00")));
  }

  @Test
  void sumValueAcceptsIntegerCents() {
    assertThat(new SumValue(new BigDecimal("250")).cents()).isEqualByComparingTo("250");
  }

  @Test
  void exactDistinctDefensivelyCopies() {
    List<DistinctMember> source = new ArrayList<>(List.of(new DistinctMember(new byte[] {1})));
    ExactDistinct exact = new ExactDistinct(source);

    source.add(new DistinctMember(new byte[] {2}));

    assertThat(exact.members()).hasSize(1);
    assertThat(exact.members()).isUnmodifiable();
  }

  @Test
  void hllDistinctDefensivelyCopiesAndComparesByContent() {
    byte[] sketch = {9, 8, 7};
    HllDistinct hll = new HllDistinct(sketch);

    sketch[0] = 0;

    assertThat(hll.sketch()).containsExactly(9, 8, 7);
    assertThat(hll).isEqualTo(new HllDistinct(new byte[] {9, 8, 7}));
    assertThat(hll.toString()).contains("HllDistinct", "3 bytes");
  }

  @Test
  void rejectsNulls() {
    assertThatNullPointerException().isThrownBy(() -> new SumValue(null));
    assertThatNullPointerException().isThrownBy(() -> new ExactDistinct(null));
    assertThatNullPointerException().isThrownBy(() -> new HllDistinct(null));
  }
}
