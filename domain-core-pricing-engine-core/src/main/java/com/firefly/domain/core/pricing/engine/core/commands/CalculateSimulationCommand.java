/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.core.commands;

import com.firefly.domain.core.pricing.engine.interfaces.dtos.SimulationCalculationResultDTO;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.fireflyframework.cqrs.command.Command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Command that drives a pricing-engine simulation calculation.
 *
 * <p>{@code productId} is optional; when {@code null} the handler resolves the
 * first product matching {@code productType} from {@code core-common-product-mgmt}.
 *
 * <p>Validation:
 * <ul>
 *   <li>{@code productType} must match {@code ^(PERSONAL_LOAN|LEASING)$}.</li>
 *   <li>{@code requestedAmount} must be positive.</li>
 *   <li>{@code term} must be at least 1 month.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateSimulationCommand implements Command<SimulationCalculationResultDTO> {

    /** Optional product identifier; resolved from {@code productType} when {@code null}. */
    private UUID productId;

    /** Mandatory product type — must be {@code PERSONAL_LOAN} or {@code LEASING}. */
    @NotNull(message = "productType is required")
    @Pattern(regexp = "^(PERSONAL_LOAN|LEASING)$", message = "productType must be PERSONAL_LOAN or LEASING")
    private String productType;

    /** Principal requested by the customer. */
    @NotNull(message = "requestedAmount is required")
    @Positive(message = "requestedAmount must be positive")
    private BigDecimal requestedAmount;

    /** Loan term in months. */
    @NotNull(message = "term is required")
    @Min(value = 1, message = "term must be at least 1 month")
    private Integer term;

    /** Optional purpose (free text, persisted upstream by the experience layer). */
    private String purpose;

    /** Optional sector classification (e.g. economic activity code). */
    private String sector;

    /** Optional asset type for leasing simulations. */
    private String assetType;

    /** ISO-4217 currency code (defaults to {@code EUR} if not provided). */
    @Builder.Default
    private String currency = "EUR";
}
