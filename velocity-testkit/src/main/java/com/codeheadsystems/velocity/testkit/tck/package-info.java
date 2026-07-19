// SPDX-License-Identifier: BSD-3-Clause

/**
 * The mandatory {@code velocity-spi} conformance TCK (ADR 0004, NFR-18).
 *
 * <p>Each {@code *Scenarios} class is a backend-neutral contract suite: constructed with a backend
 * (the specific mix-in type) plus a {@link com.codeheadsystems.velocity.testkit.MutableClock}, it
 * exposes public methods that assert one facet of the frozen contract with AssertJ. A backend
 * module drives them from its own JUnit test against a fresh store and a controllable clock, so the
 * same contract — exact aggregates, read-your-write, window aging, seed/record equivalence,
 * distinguishable failures instead of a silent {@code 0} — is verified identically for every
 * backend. The suites deliberately have no JUnit dependency so a backend picks its own test runner.
 */
@NullMarked
package com.codeheadsystems.velocity.testkit.tck;

import org.jspecify.annotations.NullMarked;
