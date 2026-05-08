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

package com.firefly.domain.core.pricing.engine.web;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Spring Boot entry point for the Core Pricing Engine domain service.
 *
 * <p>Scans the entire {@code com.firefly.domain.core.pricing.engine} package tree
 * (controllers, CQRS handlers, services, ClientFactory) and the shared
 * {@code com.firefly.common.web} configuration package.
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.firefly.domain.core.pricing.engine",
                "com.firefly.common.web"
        }
)
@EnableWebFlux
@ConfigurationPropertiesScan(basePackages = "com.firefly.domain.core.pricing.engine")
@OpenAPIDefinition(
        info = @Info(
                title = "${spring.application.name}",
                version = "${spring.application.version}",
                description = "${spring.application.description}",
                contact = @Contact(
                        name = "${spring.application.team.name}",
                        email = "${spring.application.team.email}"
                )
        ),
        servers = {
                @Server(
                        url = "http://core.getfirefly.io/domain-core-pricing-engine",
                        description = "Development Environment"
                ),
                @Server(
                        url = "/",
                        description = "Local Development Environment"
                )
        }
)
public class PricingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PricingEngineApplication.class, args);
    }
}
