/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.domain.core.pricing.engine.infra;

import com.firefly.core.product.sdk.api.ProductPricingApi;
import com.firefly.core.product.sdk.invoker.ApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * Default implementation of the ClientFactory.
 *
 * <p>Builds a single shared {@link ApiClient} pointing at the configured
 * {@code core-common-product-mgmt} base path and exposes the API beans the
 * domain layer consumes (currently only {@link ProductPricingApi}; additional
 * beans will be added in Step 4.1 as new use cases are wired in).
 */
@Component
public class ClientFactory {

    private final ApiClient apiClient;

    @Autowired
    public ClientFactory(ProductMgmtProperties productMgmtProperties) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath(productMgmtProperties.getBasePath());
    }

    @Bean
    public ProductPricingApi productPricingApi() {
        return new ProductPricingApi(apiClient);
    }
}
