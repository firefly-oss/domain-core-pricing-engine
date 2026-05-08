/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.firefly.domain.core.pricing.engine.interfaces.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of a pricing-engine simulation calculation.
 *
 * <p>The matched interest-rate bracket is embedded inline ({@code bracketMinAmount},
 * {@code bracketMaxAmount}, {@code bracketTin}) so the response does not leak the
 * downstream {@code core-common-product-mgmt} SDK types.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a pricing-engine simulation calculation.")
public class SimulationCalculationResultDTO {

    @Schema(description = "Identifier of the product the simulation was computed against.")
    private UUID productId;

    @Schema(description = "Product type (PERSONAL_LOAN, LEASING, ...).")
    private String productType;

    @Schema(description = "ISO-4217 currency code (e.g. EUR).")
    private String currency;

    @Schema(description = "Requested principal amount.")
    private BigDecimal requestedAmount;

    @Schema(description = "Loan term in months.")
    private Integer term;

    @Schema(description = "Periodic (monthly) payment, rounded to 2 decimals (HALF_EVEN).")
    private BigDecimal monthlyPayment;

    @Schema(description = "Nominal interest rate of the matched bracket (TIN, in percent).")
    private BigDecimal tin;

    @Schema(description = "Annual percentage rate (TAE, in percent), accounts for fees.")
    private BigDecimal tae;

    @Schema(description = "Total amount paid over the life of the loan (monthlyPayment * term).")
    private BigDecimal totalAmount;

    @Schema(description = "Computed opening fee amount.")
    private BigDecimal openingFee;

    @Schema(description = "Lower bound of the matched interest-rate bracket.")
    private BigDecimal bracketMinAmount;

    @Schema(description = "Upper bound of the matched interest-rate bracket.")
    private BigDecimal bracketMaxAmount;

    @Schema(description = "TIN of the matched interest-rate bracket (in percent).")
    private BigDecimal bracketTin;
}
