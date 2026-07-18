// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class DistinctMemberTest {

  @Test
  void rejectsNullToken() {
    assertThatNullPointerException().isThrownBy(() -> new DistinctMember(null));
  }

  @Test
  void constructorDefensivelyCopies() {
    byte[] source = {1, 2, 3};
    DistinctMember member = new DistinctMember(source);

    source[0] = 99;

    assertThat(member.token()).containsExactly(1, 2, 3);
  }

  @Test
  void accessorReturnsCopy() {
    DistinctMember member = new DistinctMember(new byte[] {1, 2, 3});

    byte[] first = member.token();
    first[0] = 99;

    assertThat(member.token()).containsExactly(1, 2, 3);
  }

  @Test
  void equalsAndHashCodeByContent() {
    DistinctMember a = new DistinctMember(new byte[] {4, 5, 6});
    DistinctMember b = new DistinctMember(new byte[] {4, 5, 6});
    DistinctMember c = new DistinctMember(new byte[] {4, 5, 7});

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
  }

  @Test
  void toStringDoesNotDumpRawBytes() {
    DistinctMember member = new DistinctMember(new byte[] {0x7f, 0x00, 0x41});

    assertThat(member.toString()).contains("DistinctMember", "3 bytes").doesNotContain("0x41", "A");
  }
}
