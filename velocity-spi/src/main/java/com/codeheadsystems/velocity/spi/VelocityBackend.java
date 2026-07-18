// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.spi;

import com.codeheadsystems.velocity.spi.model.BackendCapabilities;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Subject;
import org.jspecify.annotations.Nullable;

/**
 * The base of every backend: the capability descriptor plus admin erasure (ADR 0003).
 *
 * <p>A backend never implements this interface alone; it composes the aggregation mix-ins ({@link
 * CountStore}, {@link SumStore}, {@link DistinctStore}), the window-capability markers ({@link
 * SlidingSupport}, {@link TumblingSupport}), and optionally {@link SeedSupport} for the
 * capabilities it declares. The set of mix-ins implemented MUST agree with {@link #capabilities()};
 * the conformance TCK (ADR 0004) asserts that agreement.
 */
public interface VelocityBackend {

  /**
   * The single source of truth for what this backend can do (ADR 0003, P18).
   *
   * @return the backend's declared capabilities
   */
  BackendCapabilities capabilities();

  /**
   * Admin erasure of stored state (FR-23).
   *
   * <p>Erases all state for the namespace, or — when {@code subject} is non-null — only that
   * subject's state within the namespace.
   *
   * @param namespace the namespace to erase within
   * @param subject the subject to erase; {@code null} erases the whole namespace
   */
  void purge(Namespace namespace, @Nullable Subject subject);
}
