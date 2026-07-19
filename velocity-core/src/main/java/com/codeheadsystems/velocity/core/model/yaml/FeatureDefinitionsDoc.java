// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.core.model.yaml;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The YAML wire shape of a namespace's feature definition set (FR-28).
 *
 * <p>This is the canonical external representation of feature configuration — deliberately a plain,
 * Jackson-bound DTO separate from the domain {@code FeatureDefinition} so the domain types stay
 * serialization-neutral (mirroring the {@code velocity-spi} model philosophy). {@code
 * FeatureDefinitionYaml} maps between this shape and the domain. Unknown fields are tolerated on
 * read for forward compatibility.
 *
 * @param namespace the namespace these definitions belong to; may be null in a namespace-agnostic
 *     document (the caller supplies the namespace on import)
 * @param definitions the feature definitions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeatureDefinitionsDoc(
    @Nullable String namespace, @Nullable List<FeatureDefinitionDoc> definitions) {}
