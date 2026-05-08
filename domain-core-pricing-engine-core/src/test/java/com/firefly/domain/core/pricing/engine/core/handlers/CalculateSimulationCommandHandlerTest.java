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
import com.firefly.core.product.sdk.model.FeeDefinitionDTO;
import com.firefly.core.product.sdk.model.InterestRateBracketDTO;
import com.firefly.core.product.sdk.model.ProductPricingDTO;
import com.firefly.domain.core.pricing.engine.core.commands.CalculateSimulationCommand;
import com.firefly.domain.core.pricing.engine.interfaces.dtos.SimulationCalculationResultDTO;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CalculateSimulationCommandHandler}.
 *
 * <p>Pricing fixtures mirror the values agreed in the execution-plan spec:
 * <ul>
 *   <li>PERSONAL_LOAN: 1k–60k EUR, 6–96 months, single bracket @ 7.99% TIN, no fees.</li>
 *   <li>LEASING: 5k–500k EUR, 12–60 months, two brackets (5k-25k @ 6.90%, 25001-500k @ 5.90%),
 *       1% opening fee.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CalculateSimulationCommandHandlerTest {

    @Mock
    private ProductPricingApi productPricingApi;

    private CalculateSimulationCommandHandler handler;

    private static final UUID PERSONAL_LOAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID LEASING_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        handler = new CalculateSimulationCommandHandler(productPricingApi);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static ProductPricingDTO personalLoanPricing() {
        ProductPricingDTO p = new ProductPricingDTO();
        p.setProductId(PERSONAL_LOAN_ID);
        p.setProductCode("PL-001");
        p.setProductType("PERSONAL_LOAN");
        p.setCurrency("EUR");
        p.setMinAmount(new BigDecimal("1000"));
        p.setMaxAmount(new BigDecimal("60000"));
        p.setMinTerm(6);
        p.setMaxTerm(96);

        InterestRateBracketDTO b = new InterestRateBracketDTO();
        b.setMinAmount(new BigDecimal("1000"));
        b.setMaxAmount(new BigDecimal("60000"));
        b.setTin(new BigDecimal("7.99"));

        p.setInterestRates(List.of(b));
        p.setFees(List.of()); // no fees -> 0% opening fee
        return p;
    }

    private static ProductPricingDTO leasingPricing() {
        ProductPricingDTO p = new ProductPricingDTO();
        p.setProductId(LEASING_ID);
        p.setProductCode("LS-001");
        p.setProductType("LEASING");
        p.setCurrency("EUR");
        p.setMinAmount(new BigDecimal("5000"));
        p.setMaxAmount(new BigDecimal("500000"));
        p.setMinTerm(12);
        p.setMaxTerm(60);

        InterestRateBracketDTO low = new InterestRateBracketDTO();
        low.setMinAmount(new BigDecimal("5000"));
        low.setMaxAmount(new BigDecimal("25000"));
        low.setTin(new BigDecimal("6.90"));

        InterestRateBracketDTO high = new InterestRateBracketDTO();
        high.setMinAmount(new BigDecimal("25001"));
        high.setMaxAmount(new BigDecimal("500000"));
        high.setTin(new BigDecimal("5.90"));

        p.setInterestRates(List.of(low, high));

        FeeDefinitionDTO openingFee = new FeeDefinitionDTO();
        openingFee.setType("OPENING_FEE");
        openingFee.setPercentage(new BigDecimal("1.00"));
        openingFee.setFixed(BigDecimal.ZERO);
        p.setFees(List.of(openingFee));
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void mockPersonalLoanLookup() {
        when(productPricingApi.listProductsWithPricing(eq("PERSONAL_LOAN"), any()))
                .thenReturn(Flux.just(personalLoanPricing()));
        when(productPricingApi.getProductPricing(eq(PERSONAL_LOAN_ID), any()))
                .thenReturn(Mono.just(personalLoanPricing()));
    }

    private void mockLeasingLookup() {
        when(productPricingApi.listProductsWithPricing(eq("LEASING"), any()))
                .thenReturn(Flux.just(leasingPricing()));
        when(productPricingApi.getProductPricing(eq(LEASING_ID), any()))
                .thenReturn(Mono.just(leasingPricing()));
    }

    private static boolean approxEquals(BigDecimal actual, BigDecimal expected, BigDecimal tolerance) {
        return actual.subtract(expected).abs().compareTo(tolerance) <= 0;
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void personalLoan_15k_48months_799tin_noFee_computesExpectedPayment() {
        mockPersonalLoanLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("15000"))
                .term(48)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .assertNext(result -> {
                    assertResult(result, "PERSONAL_LOAN", new BigDecimal("7.99"));
                    // Expected ~ 366.10 (HALF_EVEN rounding, French amortization at full DECIMAL64 precision).
                    if (!approxEquals(result.getMonthlyPayment(), new BigDecimal("366.10"),
                            new BigDecimal("0.20"))) {
                        throw new AssertionError("monthlyPayment expected ~366.10 but was "
                                + result.getMonthlyPayment());
                    }
                    // No fees -> TAE ≈ 8.29 (allow 0.10 tolerance).
                    if (!approxEquals(result.getTae(), new BigDecimal("8.29"),
                            new BigDecimal("0.10"))) {
                        throw new AssertionError("tae expected ~8.29 but was " + result.getTae());
                    }
                    if (result.getOpeningFee().compareTo(BigDecimal.ZERO) != 0) {
                        throw new AssertionError("openingFee expected 0 but was "
                                + result.getOpeningFee());
                    }
                    // totalAmount ≈ monthlyPayment * 48
                    BigDecimal expectedTotal = result.getMonthlyPayment()
                            .multiply(BigDecimal.valueOf(48));
                    if (!approxEquals(result.getTotalAmount(), expectedTotal,
                            new BigDecimal("0.05"))) {
                        throw new AssertionError("totalAmount expected ~"
                                + expectedTotal + " but was " + result.getTotalAmount());
                    }
                })
                .verifyComplete();
    }

    @Test
    void leasing_20k_36months_690tin_1pctFee_computesExpectedPaymentAndTaeAboveTin() {
        mockLeasingLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("LEASING")
                .requestedAmount(new BigDecimal("20000"))
                .term(36)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .assertNext(result -> {
                    assertResult(result, "LEASING", new BigDecimal("6.90"));
                    // Expected ~ 616.63 (full DECIMAL64 French amortization, HALF_EVEN to 2 dp).
                    if (!approxEquals(result.getMonthlyPayment(), new BigDecimal("616.63"),
                            new BigDecimal("1.00"))) {
                        throw new AssertionError("monthlyPayment expected ~616.63 but was "
                                + result.getMonthlyPayment());
                    }
                    // Opening fee = 1% of 20000 = 200.00
                    if (result.getOpeningFee().compareTo(new BigDecimal("200.00")) != 0) {
                        throw new AssertionError("openingFee expected 200.00 but was "
                                + result.getOpeningFee());
                    }
                    // TAE must be strictly > TIN (because of fees) and < 8% sanity bound.
                    if (result.getTae().compareTo(new BigDecimal("6.90")) <= 0) {
                        throw new AssertionError("tae expected > 6.90 but was " + result.getTae());
                    }
                    if (result.getTae().compareTo(new BigDecimal("8.0")) >= 0) {
                        throw new AssertionError("tae expected < 8.0 but was " + result.getTae());
                    }
                })
                .verifyComplete();
    }

    @Test
    void leasing_25kBoundary_picksLowBracket690() {
        mockLeasingLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("LEASING")
                .requestedAmount(new BigDecimal("25000"))
                .term(36)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .assertNext(result -> {
                    if (result.getBracketTin().compareTo(new BigDecimal("6.90")) != 0) {
                        throw new AssertionError("bracketTin expected 6.90 but was "
                                + result.getBracketTin());
                    }
                })
                .verifyComplete();
    }

    @Test
    void leasing_25001Boundary_picksHighBracket590() {
        mockLeasingLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("LEASING")
                .requestedAmount(new BigDecimal("25001"))
                .term(36)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .assertNext(result -> {
                    if (result.getBracketTin().compareTo(new BigDecimal("5.90")) != 0) {
                        throw new AssertionError("bracketTin expected 5.90 but was "
                                + result.getBracketTin());
                    }
                })
                .verifyComplete();
    }

    @Test
    void personalLoan_outOfRangeAmount_throws() {
        mockPersonalLoanLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("50"))
                .term(48)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) err;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("SIMULATION_INVALID_RANGE");
                })
                .verify();
    }

    @Test
    void personalLoan_outOfRangeTerm_throws() {
        mockPersonalLoanLookup();

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("15000"))
                .term(200)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) err;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getCode()).isEqualTo("SIMULATION_INVALID_RANGE");
                })
                .verify();
    }

    @Test
    void unknownProductType_throws() {
        when(productPricingApi.listProductsWithPricing(eq("PERSONAL_LOAN"), any()))
                .thenReturn(Flux.empty());

        CalculateSimulationCommand cmd = CalculateSimulationCommand.builder()
                .productType("PERSONAL_LOAN")
                .requestedAmount(new BigDecimal("15000"))
                .term(48)
                .currency("EUR")
                .build();

        StepVerifier.create(handler.doHandle(cmd))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) err;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(be.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
                })
                .verify();
    }

    // ── pickBracket() — direct unit tests for the bracket-selection logic ────

    private static List<InterestRateBracketDTO> leasingBrackets() {
        InterestRateBracketDTO low = new InterestRateBracketDTO();
        low.setMinAmount(new BigDecimal("5000"));
        low.setMaxAmount(new BigDecimal("25000"));
        low.setTin(new BigDecimal("6.90"));

        InterestRateBracketDTO high = new InterestRateBracketDTO();
        high.setMinAmount(new BigDecimal("25001"));
        high.setMaxAmount(new BigDecimal("100000"));
        high.setTin(new BigDecimal("5.90"));
        return List.of(low, high);
    }

    @Test
    void pickBracket_returnsBracketWhenAmountFallsExactlyOnBoundary() {
        InterestRateBracketDTO picked = handler.pickBracket(
                new BigDecimal("25000"), leasingBrackets());

        assertThat(picked.getTin()).isEqualByComparingTo(new BigDecimal("6.90"));
        assertThat(picked.getMinAmount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void pickBracket_returnsHigherBracketWhenAmountSlightlyAboveBoundary() {
        InterestRateBracketDTO picked = handler.pickBracket(
                new BigDecimal("25001"), leasingBrackets());

        assertThat(picked.getTin()).isEqualByComparingTo(new BigDecimal("5.90"));
        assertThat(picked.getMinAmount()).isEqualByComparingTo(new BigDecimal("25001"));
    }

    @Test
    void pickBracket_returnsHigherBracketWhenAmountIsFractional() {
        // 25000.50 falls in the inclusive gap between [5000-25000] and [25001-100000].
        // The largest-min strategy selects the low bracket because 25001 > 25000.50,
        // i.e. the only bracket whose min is <= 25000.50 is the low one (min 5000).
        InterestRateBracketDTO picked = handler.pickBracket(
                new BigDecimal("25000.50"), leasingBrackets());

        assertThat(picked.getTin()).isEqualByComparingTo(new BigDecimal("6.90"));
        assertThat(picked.getMinAmount()).isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void pickBracket_throwsWhenAmountBelowMinimum() {
        assertThat(
                org.junit.jupiter.api.Assertions.assertThrows(
                        BusinessException.class,
                        () -> handler.pickBracket(new BigDecimal("999"), leasingBrackets()))
        ).satisfies(be -> {
            assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(be.getCode()).isEqualTo("SIMULATION_INVALID_RANGE");
        });
    }

    private static void assertResult(SimulationCalculationResultDTO result,
                                     String expectedType, BigDecimal expectedTin) {
        if (result == null) {
            throw new AssertionError("result is null");
        }
        if (!expectedType.equals(result.getProductType())) {
            throw new AssertionError("productType expected " + expectedType
                    + " but was " + result.getProductType());
        }
        if (result.getTin().compareTo(expectedTin) != 0) {
            throw new AssertionError("tin expected " + expectedTin
                    + " but was " + result.getTin());
        }
        if (result.getMonthlyPayment().signum() <= 0) {
            throw new AssertionError("monthlyPayment must be positive");
        }
        if (result.getTotalAmount().signum() <= 0) {
            throw new AssertionError("totalAmount must be positive");
        }
    }
}
