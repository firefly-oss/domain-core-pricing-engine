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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Read endpoints for product pricing exposed by the pricing-engine domain.
 *
 * <p>These endpoints simply forward to {@code core-common-product-mgmt} via
 * cached CQRS query handlers — they exist so the experience layer can hit a
 * single domain endpoint regardless of where the data physically lives.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pricing-engine/products")
@Tag(name = "Pricing Engine - Products",
        description = "Read endpoints exposing pricing definitions sourced from core-common-product-mgmt")
public class PricingEngineQueryController {

    private final PricingEngineService pricingEngineService;

    @Operation(
            operationId = "getEngineProductPricing",
            summary = "Get product pricing",
            description = "Retrieves the pricing definition for a single product."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pricing retrieved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductPricingDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid product identifier", content = @Content),
            @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    @GetMapping(value = "/{productId}/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ProductPricingDTO>> getProductPricing(
            @Parameter(description = "Unique identifier of the product", required = true)
            @PathVariable UUID productId) {
        log.debug("Fetching pricing for productId={}", productId);
        return pricingEngineService.getProductPricing(productId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @Operation(
            operationId = "listEngineProductsWithPricing",
            summary = "List products with pricing",
            description = "Lists products with pricing, optionally filtered by productType."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Products listed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ProductPricingDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter", content = @Content),
            @ApiResponse(responseCode = "404", description = "No products available", content = @Content)
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ProductPricingDTO> listProductsWithPricing(
            @Parameter(description = "Optional product-type filter (e.g. PERSONAL_LOAN, LEASING)")
            @RequestParam(value = "productType", required = false) String productType) {
        log.debug("Listing products with pricing, productType={}", productType);
        return pricingEngineService.listProductsWithPricing(productType);
    }
}
