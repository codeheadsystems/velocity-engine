// SPDX-License-Identifier: BSD-3-Clause
/**
 * The Redis/Lettuce v1 SLIDING hot-path reference backend: an exact, atomic, read-your-write {@link
 * com.codeheadsystems.velocity.spi.VelocityBackend} over true sliding windows, backed by sorted
 * sets and atomic Lua apply scripts. See {@link
 * com.codeheadsystems.velocity.backend.redis.RedisVelocityBackend}.
 */
@NullMarked
package com.codeheadsystems.velocity.backend.redis;

import org.jspecify.annotations.NullMarked;
