// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi.model;

/**
 * A distinguishable data-plane failure reason (ADR 0009, NFR-19).
 *
 * <p>A {@link FeatureResult.Failure} carries one of these instead of a silent {@code 0}, so a
 * synchronous authorization caller can choose fail-open vs. fail-closed deterministically.
 *
 * <p><strong>This enum is extensible: adding a code is an ADDITIVE change (ADR 0009 rule
 * 2).</strong> Consumers MUST tolerate a code they do not recognize by treating it as a generic
 * failure rather than crashing on an unknown value. New codes are expected here as the enforcement
 * behavior (Tier-2 deadline/load-shed ADRs) lands.
 */
public enum FailureCode {
  /** The backend is down/partitioned and could not produce a value (NFR-19). */
  UNAVAILABLE,
  /** The caller's deadline was hit before a result was available (NFR-19). */
  DEADLINE_EXCEEDED,
  /** A sliding exact-distinct feature exceeded its cardinality cap (ADR 0005). */
  CARDINALITY_CAP_EXCEEDED,
  /** The requested window is not supported by the backend (FR-13). */
  UNSUPPORTED_WINDOW,
  /** The request was malformed. */
  VALIDATION,
  /** An unexpected internal error. */
  INTERNAL
}
