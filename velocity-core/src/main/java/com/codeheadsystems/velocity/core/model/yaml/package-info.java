// SPDX-License-Identifier: BSD-3-Clause

/**
 * The Jackson-bound YAML wire shapes for feature-definition import/export (FR-28).
 *
 * <p>Kept deliberately separate from the domain {@code FeatureDefinition} so the domain stays
 * serialization-neutral: {@code FeatureDefinitionYaml} maps between these DTOs and the domain.
 * These are coverage-exempt DTOs under {@code **}/{@code model/**}.
 */
@NullMarked
package com.codeheadsystems.velocity.core.model.yaml;

import org.jspecify.annotations.NullMarked;
