// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The pre-computed aggregate seeded into a single bucket (ADR 0008).
 *
 * <p>A seed supplies aggregate state at bucket granularity (never a single total, which cannot be
 * apportioned across a window's buckets — ADR 0008). Each variant matches an aggregation kind:
 * {@link CountValue} for COUNT, {@link SumValue} for SUM, and {@link ExactDistinct} / {@link
 * HllDistinct} for DISTINCT.
 */
public sealed interface SeedAggregate
    permits SeedAggregate.CountValue,
        SeedAggregate.SumValue,
        SeedAggregate.ExactDistinct,
        SeedAggregate.HllDistinct {

  /**
   * A seeded COUNT.
   *
   * @param count the bucket's count
   */
  record CountValue(long count) implements SeedAggregate {}

  /**
   * A seeded SUM in integer cents (P3).
   *
   * @param cents the bucket's summed cents; {@link BigDecimal#scale() scale} must be 0
   */
  record SumValue(BigDecimal cents) implements SeedAggregate {

    /** Validates the value is present and is integer cents (scale 0). */
    public SumValue {
      Objects.requireNonNull(cents, "cents");
      if (cents.scale() != 0) {
        throw new IllegalArgumentException(
            "cents must be integer cents (scale 0), was scale " + cents.scale());
      }
    }
  }

  /**
   * A seeded exact DISTINCT member set. Exact members migrate across backends (ADR 0008), subject
   * to the exact clamp (ADR 0006).
   *
   * @param members the bucket's distinct members; defensively copied to an unmodifiable list
   */
  record ExactDistinct(List<DistinctMember> members) implements SeedAggregate {

    /** Stores an unmodifiable defensive copy of the members. */
    public ExactDistinct {
      Objects.requireNonNull(members, "members");
      members = List.copyOf(members);
    }
  }

  /**
   * A seeded pre-computed HLL DISTINCT sketch (tumbling only, ADR 0005).
   *
   * <p>The sketch is <strong>opaque, same-implementation-only bytes</strong> (ADR 0006 interop
   * caveat): a backend accepts an {@code HllDistinct} seed only from its own implementation and
   * MUST reject a foreign one. HLL sketches do <em>not</em> migrate across backends; only exact
   * members do (ADR 0008). The bytes are defensively copied and never interpreted here.
   *
   * @param sketch the opaque HLL sketch bytes
   */
  // Opaque value type: the array component is defensively copied and equality/hashCode are by
  // content (Arrays.*), so the default reference-based semantics ArrayRecordComponent warns about
  // do not apply here.
  @SuppressWarnings("ArrayRecordComponent")
  record HllDistinct(byte[] sketch) implements SeedAggregate {

    /** Validates the sketch is present and stores a defensive copy. */
    public HllDistinct {
      Objects.requireNonNull(sketch, "sketch");
      sketch = sketch.clone();
    }

    /**
     * Returns a defensive copy of the opaque sketch bytes.
     *
     * @return a copy of the sketch bytes
     */
    @Override
    public byte[] sketch() {
      return sketch.clone();
    }

    /** Content-based equality over the sketch bytes. */
    @Override
    public boolean equals(Object o) {
      return o instanceof HllDistinct other && Arrays.equals(sketch, other.sketch);
    }

    /** Content-based hash over the sketch bytes. */
    @Override
    public int hashCode() {
      return Arrays.hashCode(sketch);
    }

    /** Readable representation that does not dump the raw opaque bytes. */
    @Override
    public String toString() {
      return "HllDistinct[" + sketch.length + " bytes]";
    }
  }
}
