// SPDX-License-Identifier: BSD-3-Clause

/**
 * The value types of the {@code velocity-core} engine: feature definitions, the namespace-scoped
 * definition snapshot, subject-source model, and validation/fan-out result records.
 *
 * <p>These are plain, immutable records/sealed types that validate their invariants in compact
 * constructors — the engine's configuration and result vocabulary, kept separate from the engine
 * <em>logic</em> (which lives in the parent {@code com.codeheadsystems.velocity.core} package and
 * is tested to the coverage gate). This package is coverage-exempt (the {@code **}/{@code model/**}
 * carve-out); no engine logic is parked here.
 */
@NullMarked
package com.codeheadsystems.velocity.core.model;

import org.jspecify.annotations.NullMarked;
