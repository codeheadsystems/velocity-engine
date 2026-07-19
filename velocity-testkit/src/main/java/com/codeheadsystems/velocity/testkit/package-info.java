// SPDX-License-Identifier: BSD-3-Clause

/**
 * The velocity-testkit: the in-memory reference {@code velocity-spi} backend and its time helpers.
 *
 * <p>{@link com.codeheadsystems.velocity.testkit.InMemoryVelocityBackend} is the exact, atomic,
 * thread-safe reference implementation that proves the frozen SPI contract is implementable (ADR
 * 0004, NFR-13/18); {@link com.codeheadsystems.velocity.testkit.MutableClock} and {@link
 * com.codeheadsystems.velocity.testkit.WindowMath} let the conformance TCK ({@link
 * com.codeheadsystems.velocity.testkit.tck}) drive window aging deterministically. Every {@code
 * velocity-backend-*} must pass that TCK for its declared capabilities.
 */
@NullMarked
package com.codeheadsystems.velocity.testkit;

import org.jspecify.annotations.NullMarked;
