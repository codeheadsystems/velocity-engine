// SPDX-License-Identifier: BSD-3-Clause

/**
 * The v1 Postgres/JDBI tumbling reference backend (requirements §8, acceptance #1).
 *
 * <p>{@link com.codeheadsystems.velocity.backend.jdbi.JdbiVelocityBackend} is a real
 * Postgres-backed {@link com.codeheadsystems.velocity.spi.VelocityBackend} that implements the
 * {@link com.codeheadsystems.velocity.spi.CountStore COUNT}, {@link
 * com.codeheadsystems.velocity.spi.SumStore SUM} and {@link
 * com.codeheadsystems.velocity.spi.DistinctStore DISTINCT} aggregation mix-ins over {@link
 * com.codeheadsystems.velocity.spi.TumblingSupport tumbling} windows, plus {@link
 * com.codeheadsystems.velocity.spi.SeedSupport seeding}. It is <strong>exact and atomic</strong>:
 * every value is {@link com.codeheadsystems.velocity.spi.model.Exactness#EXACT EXACT} and produced
 * under {@link com.codeheadsystems.velocity.spi.model.ReadYourWriteLevel#ATOMIC ATOMIC}
 * read-your-write, via a Postgres {@code INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING} upsert
 * (ADR 0007).
 *
 * <p><strong>Tumbling only.</strong> The backend owns bucketing and is the clock authority (ADR
 * 0003, FR-3): a tumbling window's value is the current aligned bucket {@code
 * [floor(nowMs/durationMs)·durationMs, +durationMs)} only. It declares no sliding windows and does
 * not implement {@link com.codeheadsystems.velocity.spi.SlidingSupport}.
 *
 * <p><strong>Distinct is exact-only.</strong> Members are stored as opaque {@code BYTEA} rows and
 * cardinality is a {@code COUNT(*)}; HLL degradation (ADR 0005/0006) is a documented follow-up, so
 * the backend declares an effectively-unbounded exact clamp and rejects an {@code HllDistinct}
 * seed.
 */
@NullMarked
package com.codeheadsystems.velocity.backend.jdbi;

import org.jspecify.annotations.NullMarked;
