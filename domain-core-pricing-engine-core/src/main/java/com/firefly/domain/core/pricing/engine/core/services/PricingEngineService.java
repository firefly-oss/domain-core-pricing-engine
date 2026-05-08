/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.core.services;

import com.firefly.core.product.sdk.model.ProductPricingDTO;
import com.firefly.domain.core.pricing.engine.core.commands.CalculateSimulationCommand;
import com.firefly.domain.core.pricing.engine.interfaces.dtos.SimulationCalculationResultDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Domain service exposing the pricing-engine read and calculation API.
 *
 * <p>All state-less work is dispatched through the CQRS {@code QueryBus}
 * / {@code CommandBus} so it benefits from the platform's observability,
 * caching, and validation layers.
 */
public interface PricingEngineService {

    /**
     * Retrieves the pricing definition for a single product.
     *
     * @param productId product identifier
     * @return the pricing DTO
     */
    Mono<ProductPricingDTO> getProductPricing(UUID productId);

    /**
     * Lists products with pricing, optionally filtered by {@code productType}.
     *
     * @param productType optional product-type filter
     * @return reactive stream of pricing DTOs
     */
    Flux<ProductPricingDTO> listProductsWithPricing(String productType);

    /**
     * Computes a pricing simulation given a request command.
     *
     * @param command simulation parameters (amount, term, type, ...)
     * @return the calculation result
     */
    Mono<SimulationCalculationResultDTO> calculateSimulation(CalculateSimulationCommand command);
}
