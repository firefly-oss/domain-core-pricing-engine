/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.web.controllers;

import com.firefly.domain.core.pricing.engine.core.commands.CalculateSimulationCommand;
import com.firefly.domain.core.pricing.engine.core.services.PricingEngineService;
import com.firefly.domain.core.pricing.engine.interfaces.dtos.SimulationCalculationResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Calculation endpoint for pricing-engine simulations.
 *
 * <p>The handler computes the periodic payment, TIN, TAE, opening fee and total
 * amount for the requested principal/term/product combination.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pricing-engine/simulations")
@Tag(name = "Pricing Engine - Simulation",
        description = "Pricing-engine simulation calculations (monthly payment, TIN, TAE, fees)")
public class SimulationCalculationController {

    private final PricingEngineService pricingEngineService;

    @Operation(
            operationId = "calculateSimulation",
            summary = "Calculate a pricing simulation",
            description = "Computes the monthly payment, TIN, TAE, opening fee and total " +
                    "amount for the requested simulation parameters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simulation calculated successfully",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SimulationCalculationResultDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid simulation parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "No matching product or bracket found", content = @Content)
    })
    @PostMapping(
            value = "/calculate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<SimulationCalculationResultDTO>> calculateSimulation(
            @Valid @RequestBody CalculateSimulationCommand command) {
        log.info("Calculating simulation: productType={}, requestedAmount={}, term={}",
                command.getProductType(), command.getRequestedAmount(), command.getTerm());
        return pricingEngineService.calculateSimulation(command)
                .map(ResponseEntity::ok);
    }
}
