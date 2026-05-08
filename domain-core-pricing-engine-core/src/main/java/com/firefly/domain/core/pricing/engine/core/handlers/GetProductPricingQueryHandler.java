/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.core.handlers;

import com.firefly.core.product.sdk.api.ProductPricingApi;
import com.firefly.core.product.sdk.model.ProductPricingDTO;
import com.firefly.domain.core.pricing.engine.core.queries.GetProductPricingQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.QueryHandlerComponent;
import org.fireflyframework.cqrs.query.QueryHandler;
import reactor.core.publisher.Mono;

/**
 * CQRS query handler that fetches a {@link ProductPricingDTO} for a single product
 * from {@code core-common-product-mgmt}.
 *
 * <p>Cached for 60 seconds. Trade-off: pricing changes propagated from
 * {@code core-common-product-mgmt} take up to 60s to be visible here, which
 * keeps the regulatory disclosure window (MiFID/PSD2) tight enough that a
 * borrower simulated at the old rate will not normally onboard at a new rate.
 * If a stricter SLA is required, switch to invalidate-on-config-change events
 * published by {@code core-common-product-mgmt} instead of relying on TTL.
 */
@Slf4j
@QueryHandlerComponent(cacheable = true, cacheTtl = 60)
@RequiredArgsConstructor
public class GetProductPricingQueryHandler
        extends QueryHandler<GetProductPricingQuery, ProductPricingDTO> {

    private final ProductPricingApi productPricingApi;

    @Override
    protected Mono<ProductPricingDTO> doHandle(GetProductPricingQuery query) {
        return productPricingApi.getProductPricing(query.getProductId(), null);
    }
}
