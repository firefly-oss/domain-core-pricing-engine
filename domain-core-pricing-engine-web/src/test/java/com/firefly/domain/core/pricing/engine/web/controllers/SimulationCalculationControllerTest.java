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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WebTestClient tests for {@link SimulationCalculationController}.
 */
@ExtendWith(MockitoExtension.class)
class SimulationCalculationControllerTest {

    @Mock
    private PricingEngineService pricingEngineService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        SimulationCalculationController controller =
                new SimulationCalculationController(pricingEngineService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void calculateSimulation_validRequest_returns200() {
        SimulationCalculationResultDTO expected = SimulationCalculationResultDTO.builder()
                .productId(UUID.randomUUID())
                .productType("PERSONAL_LOAN")
                .currency("EUR")
                .requestedAmount(new BigDecimal("15000"))
                .term(48)
                .monthlyPayment(new BigDecimal("366.06"))
                .tin(new BigDecimal("7.99"))
                .tae(new BigDecimal("8.2929"))
                .totalAmount(new BigDecimal("17570.88"))
                .openingFee(BigDecimal.ZERO)
                .bracketMinAmount(new BigDecimal("1000"))
                .bracketMaxAmount(new BigDecimal("60000"))
                .bracketTin(new BigDecimal("7.99"))
                .build();

        when(pricingEngineService.calculateSimulation(any(CalculateSimulationCommand.class)))
                .thenReturn(Mono.just(expected));

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("15000"))
                .term(48)
                .currency("EUR")
                .build();

        webTestClient.post()
                .uri("/api/v1/pricing-engine/simulations/calculate")
                .bodyValue(cmd)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SimulationCalculationResultDTO.class)
                .value(r -> {
                    if (r.getMonthlyPayment() == null) {
                        throw new AssertionError("monthlyPayment was null");
                    }
                    if (!"PERSONAL_LOAN".equals(r.getProductType())) {
                        throw new AssertionError("productType mismatch");
                    }
                });
    }

    @Test
    void calculateSimulation_missingRequestedAmount_returns400() {
        // Build the JSON manually so we can omit requestedAmount.
        Map<String, Object> body = Map.of(
                "productType", "PERSONAL_LOAN",
                "term", 48,
                "currency", "EUR"
        );

        webTestClient.post()
                .uri("/api/v1/pricing-engine/simulations/calculate")
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
