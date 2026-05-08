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

import com.firefly.core.product.sdk.model.ProductPricingDTO;
import com.firefly.domain.core.pricing.engine.core.services.PricingEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PricingEngineQueryController}.
 */
@ExtendWith(MockitoExtension.class)
class PricingEngineQueryControllerTest {

    @Mock
    private PricingEngineService pricingEngineService;

    private PricingEngineQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new PricingEngineQueryController(pricingEngineService);
    }

    @Test
    void getProductPricing_returnsOk() {
        UUID productId = UUID.randomUUID();
        ProductPricingDTO dto = new ProductPricingDTO();
        dto.setProductId(productId);
        dto.setProductType("PERSONAL_LOAN");
        dto.setMinAmount(new BigDecimal("1000"));

        when(pricingEngineService.getProductPricing(productId)).thenReturn(Mono.just(dto));

        StepVerifier.create(controller.getProductPricing(productId))
                .assertNext(entity -> {
                    if (entity.getStatusCode() != HttpStatus.OK) {
                        throw new AssertionError("expected 200 OK but was " + entity.getStatusCode());
                    }
                    if (entity.getBody() == null || !productId.equals(entity.getBody().getProductId())) {
                        throw new AssertionError("response body did not match the requested product id");
                    }
                })
                .verifyComplete();
    }

    @Test
    void getProductPricing_emptyMonoReturns404() {
        UUID productId = UUID.randomUUID();
        when(pricingEngineService.getProductPricing(productId)).thenReturn(Mono.empty());

        StepVerifier.create(controller.getProductPricing(productId))
                .assertNext(entity -> {
                    if (entity.getStatusCode() != HttpStatus.NOT_FOUND) {
                        throw new AssertionError("expected 404 NOT_FOUND but was " + entity.getStatusCode());
                    }
                })
                .verifyComplete();
    }

    @Test
    void listProductsWithPricing_filtersByType() {
        ProductPricingDTO dto = new ProductPricingDTO();
        dto.setProductId(UUID.randomUUID());
        dto.setProductType("LEASING");

        when(pricingEngineService.listProductsWithPricing(any())).thenReturn(Flux.just(dto));

        StepVerifier.create(controller.listProductsWithPricing("LEASING"))
                .expectNext(dto)
                .verifyComplete();
    }
}
