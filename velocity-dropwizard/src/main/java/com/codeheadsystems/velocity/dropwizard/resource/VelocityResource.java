// SPDX-License-Identifier: BSD-3-Clause
package com.codeheadsystems.velocity.dropwizard.resource;

import com.codeheadsystems.velocity.core.VelocityEngine;
import com.codeheadsystems.velocity.dropwizard.api.Wire;
import com.codeheadsystems.velocity.dropwizard.api.WireMapper;
import com.codeheadsystems.velocity.spi.model.FeatureResult;
import com.codeheadsystems.velocity.spi.model.Namespace;
import com.codeheadsystems.velocity.spi.model.Subject;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The velocity HTTP surface (AR-2): record, query, and capabilities, all namespace-scoped. */
@Path("/v1/{namespace}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public final class VelocityResource {

  private final VelocityEngine engine;
  private final String backendName;

  /**
   * @param engine the counting engine.
   * @param backendName the logical backend name whose capabilities are reported.
   */
  @Inject
  public VelocityResource(
      final VelocityEngine engine, @Named("backendName") final String backendName) {
    this.engine = engine;
    this.backendName = backendName;
  }

  /** Records an event and returns the post-increment per-feature velocities (FR-2). */
  @POST
  @Path("/record")
  public Wire.RecordResponse record(
      @PathParam("namespace") final String namespace, final Wire.RecordRequest request) {
    final Subject subject = new Subject(request.subject().type(), request.subject().value());
    final Map<String, String> dimensions =
        request.dimensions() == null ? Map.of() : request.dimensions();
    final BigDecimal value = request.value() == null ? null : new BigDecimal(request.value());
    return WireMapper.toRecordResponse(
        engine.record(new Namespace(namespace), subject, dimensions, value));
  }

  /** Queries the named features for a subject (FR-6). */
  @POST
  @Path("/query")
  public Wire.QueryResponse query(
      @PathParam("namespace") final String namespace, final Wire.QueryRequest request) {
    final Subject subject = new Subject(request.subject().type(), request.subject().value());
    final Map<String, List<FeatureResult>> results =
        engine.query(new Namespace(namespace), subject, request.features());
    final Map<String, List<Wire.ResultDto>> wire = new LinkedHashMap<>();
    results.forEach(
        (name, list) -> wire.put(name, list.stream().map(WireMapper::toResult).toList()));
    return new Wire.QueryResponse(wire);
  }

  /** Returns the active backend's declared capabilities (FR-12). */
  @GET
  @Path("/capabilities")
  public Wire.CapabilitiesResponse capabilities(@PathParam("namespace") final String namespace) {
    return WireMapper.toCapabilities(engine.capabilities(backendName));
  }
}
