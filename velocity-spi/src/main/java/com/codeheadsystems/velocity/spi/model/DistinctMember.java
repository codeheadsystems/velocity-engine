// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * An opaque, pre-hashed distinct dimension value (ADR 0003, ADR 0009; FR-38).
 *
 * <p>The token is a fixed-width, keyed-hashed representation of a dimension value produced by the
 * core (FR-38 stores 16-byte truncated HMACs, not raw IPs/tokens). It is <strong>opaque</strong>:
 * the backend MUST NOT interpret, parse, or reverse the bytes — it only stores them and counts
 * distinct occurrences. Treated as a value type: the bytes are defensively copied on construction
 * and on access, and equality/hashing are by content.
 *
 * @param token the opaque token bytes; never null, never interpreted by the backend
 */
// Opaque value type: the array component is defensively copied and equality/hashCode are by content
// (Arrays.*), so the default reference-based semantics ArrayRecordComponent warns about do not
// apply.
@SuppressWarnings("ArrayRecordComponent")
public record DistinctMember(byte[] token) {

  /** Validates the token is present and stores a defensive copy so the value is immutable. */
  public DistinctMember {
    Objects.requireNonNull(token, "token");
    token = token.clone();
  }

  /**
   * Returns a defensive copy of the token bytes; mutating the returned array does not affect this
   * value.
   *
   * @return a copy of the opaque token bytes
   */
  @Override
  public byte[] token() {
    return token.clone();
  }

  /** Content-based equality: two members are equal iff their token bytes are equal. */
  @Override
  public boolean equals(Object o) {
    return o instanceof DistinctMember other && Arrays.equals(token, other.token);
  }

  /** Content-based hash over the token bytes. */
  @Override
  public int hashCode() {
    return Arrays.hashCode(token);
  }

  /** Readable representation that does not dump the raw opaque bytes. */
  @Override
  public String toString() {
    return "DistinctMember[" + token.length + " bytes]";
  }
}
