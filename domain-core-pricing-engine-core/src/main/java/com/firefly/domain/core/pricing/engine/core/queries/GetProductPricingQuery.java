/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.core.queries;

import com.firefly.core.product.sdk.model.ProductPricingDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.query.Query;

import java.util.UUID;

/**
 * Query to retrieve the pricing definition of a single product from
 * {@code core-common-product-mgmt}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetProductPricingQuery implements Query<ProductPricingDTO> {

    /** Product identifier. */
    private UUID productId;
}
