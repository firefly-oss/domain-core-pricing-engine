/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.core.services.impl;

import com.firefly.core.product.sdk.model.ProductPricingDTO;
import com.firefly.domain.core.pricing.engine.core.commands.CalculateSimulationCommand;
import com.firefly.domain.core.pricing.engine.core.queries.GetProductPricingQuery;
import com.firefly.domain.core.pricing.engine.core.queries.ListProductsWithPricingQuery;
import com.firefly.domain.core.pricing.engine.core.services.PricingEngineService;
import com.firefly.domain.core.pricing.engine.interfaces.dtos.SimulationCalculationResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Default {@link PricingEngineService} implementation.
 *
 * <p>Reads go through {@link QueryBus} (cacheable handlers); the calculation
 * goes through {@link CommandBus} so it is fully traced/metricked alongside
 * other CQRS commands.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingEngineServiceImpl implements PricingEngineService {

    private final QueryBus queryBus;
    private final CommandBus commandBus;

    @Override
    public Mono<ProductPricingDTO> getProductPricing(UUID productId) {
        return queryBus.query(GetProductPricingQuery.builder()
                .productId(productId)
                .build());
    }

    @Override
    public Flux<ProductPricingDTO> listProductsWithPricing(String productType) {
        return queryBus.<List<ProductPricingDTO>>query(ListProductsWithPricingQuery.builder()
                        .productType(productType)
                        .build())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<SimulationCalculationResultDTO> calculateSimulation(CalculateSimulationCommand command) {
        return commandBus.send(command);
    }
}
