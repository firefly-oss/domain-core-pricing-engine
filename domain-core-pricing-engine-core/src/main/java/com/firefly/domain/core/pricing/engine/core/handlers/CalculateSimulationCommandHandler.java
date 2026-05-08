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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import org.fireflyframework.web.error.exceptions.BusinessException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * CQRS command handler that performs a pricing-engine simulation calculation.
 *
 * <p>Algorithm overview:
 * <ol>
 *   <li>Resolve the target {@link ProductPricingDTO} from the SDK
 *       (either by id or by product-type lookup).</li>
 *   <li>Validate amount/term against the product min/max range.</li>
 *   <li>Pick the matching {@link InterestRateBracketDTO} for the requested amount.</li>
 *   <li>Compute the monthly payment using French amortization.</li>
 *   <li>Compute the {@code OPENING_FEE} amount (if defined).</li>
 *   <li>Compute the TAE (annual effective rate) — analytically when no fees,
 *       via Newton-Raphson IRR otherwise.</li>
 *   <li>Round monetary outputs to 2 decimals (HALF_EVEN); keep TAE at 4 decimals.</li>
 * </ol>
 *
 * <p>All arithmetic uses {@link BigDecimal} with {@link MathContext#DECIMAL64} —
 * no {@code double} for monetary calculations.
 */
@Slf4j
@CommandHandlerComponent
@RequiredArgsConstructor
public class CalculateSimulationCommandHandler
        extends CommandHandler<CalculateSimulationCommand, SimulationCalculationResultDTO> {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal NEWTON_TOLERANCE = new BigDecimal("0.000000001"); // 1e-9
    private static final int NEWTON_MAX_ITERATIONS = 50;

    private final ProductPricingApi productPricingApi;

    @Override
    protected Mono<SimulationCalculationResultDTO> doHandle(CalculateSimulationCommand cmd) {
        return resolvePricing(cmd)
                .map(pricing -> compute(cmd, pricing));
    }

    // ── Pricing resolution ────────────────────────────────────────────────────

    private Mono<ProductPricingDTO> resolvePricing(CalculateSimulationCommand cmd) {
        if (cmd.getProductId() != null) {
            return productPricingApi.getProductPricing(cmd.getProductId(), null);
        }
        return productPricingApi.listProductsWithPricing(cmd.getProductType(), null)
                .next()
                .switchIfEmpty(Mono.error(new BusinessException(
                        HttpStatus.NOT_FOUND,
                        "PRODUCT_NOT_FOUND",
                        "No product found with productType=" + cmd.getProductType())))
                .flatMap(p -> productPricingApi.getProductPricing(p.getProductId(), null));
    }

    // ── Core calculation ──────────────────────────────────────────────────────

    private SimulationCalculationResultDTO compute(CalculateSimulationCommand cmd,
                                                   ProductPricingDTO pricing) {
        validateRange(cmd, pricing);
        InterestRateBracketDTO bracket = pickBracket(cmd.getRequestedAmount(), pricing.getInterestRates());

        BigDecimal principal = cmd.getRequestedAmount();
        int n = cmd.getTerm();
        BigDecimal tin = bracket.getTin();
        BigDecimal i = tin.divide(HUNDRED, MC).divide(TWELVE, MC); // monthly nominal rate

        BigDecimal monthlyPaymentRaw = monthlyPayment(principal, i, n);
        BigDecimal monthlyPayment = monthlyPaymentRaw.setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal openingFee = openingFeeAmount(principal, pricing.getFees())
                .setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal totalAmount = monthlyPayment
                .multiply(BigDecimal.valueOf(n), MC)
                .setScale(2, RoundingMode.HALF_EVEN);

        BigDecimal tae = computeTae(principal, openingFee, monthlyPaymentRaw, n, i)
                .setScale(4, RoundingMode.HALF_EVEN);

        return SimulationCalculationResultDTO.builder()
                .productId(pricing.getProductId())
                .productType(pricing.getProductType())
                .currency(pricing.getCurrency() != null ? pricing.getCurrency() : cmd.getCurrency())
                .requestedAmount(principal)
                .term(n)
                .monthlyPayment(monthlyPayment)
                .tin(tin)
                .tae(tae)
                .totalAmount(totalAmount)
                .openingFee(openingFee)
                .bracketMinAmount(bracket.getMinAmount())
                .bracketMaxAmount(bracket.getMaxAmount())
                .bracketTin(bracket.getTin())
                .build();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateRange(CalculateSimulationCommand cmd, ProductPricingDTO pricing) {
        if (pricing.getMinAmount() != null
                && cmd.getRequestedAmount().compareTo(pricing.getMinAmount()) < 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIMULATION_INVALID_RANGE",
                    "requestedAmount " + cmd.getRequestedAmount()
                            + " is below product minimum " + pricing.getMinAmount());
        }
        if (pricing.getMaxAmount() != null
                && cmd.getRequestedAmount().compareTo(pricing.getMaxAmount()) > 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIMULATION_INVALID_RANGE",
                    "requestedAmount " + cmd.getRequestedAmount()
                            + " exceeds product maximum " + pricing.getMaxAmount());
        }
        if (pricing.getMinTerm() != null && cmd.getTerm() < pricing.getMinTerm()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIMULATION_INVALID_RANGE",
                    "term " + cmd.getTerm()
                            + " is below product minimum " + pricing.getMinTerm());
        }
        if (pricing.getMaxTerm() != null && cmd.getTerm() > pricing.getMaxTerm()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SIMULATION_INVALID_RANGE",
                    "term " + cmd.getTerm()
                            + " exceeds product maximum " + pricing.getMaxTerm());
        }
    }

    /**
     * Selects the interest-rate bracket whose {@code minAmount} is the largest value
     * still {@code <= amount}.
     *
     * <p>Rationale: bracket bounds in the V12 seed are inclusive on both ends with a
     * {@code +1} gap between contiguous brackets (e.g., {@code [5000-25000]} and
     * {@code [25001-100000]}). A literal {@code min <= amount <= max} match leaves
     * fractional amounts such as {@code 25000.50} without any matching bracket. By
     * filtering on {@code min <= amount} and picking the bracket with the largest
     * {@code min}, every amount within the product's overall {@code minAmount} /
     * {@code maxAmount} range maps to exactly one bracket — no gaps, no overlap
     * surprises. {@link #validateRange} already rejects amounts outside the product
     * range before this method is called, so the only failure path here is a
     * misconfigured product whose smallest bracket {@code min} exceeds the request.
     */
    InterestRateBracketDTO pickBracket(BigDecimal amount,
                                       List<InterestRateBracketDTO> brackets) {
        if (brackets == null || brackets.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                    "Product has no interest-rate brackets configured");
        }
        return brackets.stream()
                .filter(b -> b.getMinAmount() != null
                        && amount.compareTo(b.getMinAmount()) >= 0)
                .max(Comparator.comparing(InterestRateBracketDTO::getMinAmount))
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, "SIMULATION_INVALID_RANGE",
                        "Amount " + amount + " is below the minimum supported by any pricing bracket"));
    }

    // ── French amortization ───────────────────────────────────────────────────

    private BigDecimal monthlyPayment(BigDecimal principal, BigDecimal i, int n) {
        if (i.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(n), MC);
        }
        BigDecimal onePlusIPowN = ONE.add(i, MC).pow(n, MC);
        BigDecimal numerator = i.multiply(onePlusIPowN, MC);
        BigDecimal denominator = onePlusIPowN.subtract(ONE, MC);
        return principal.multiply(numerator, MC).divide(denominator, MC);
    }

    // ── Fees ──────────────────────────────────────────────────────────────────

    private BigDecimal openingFeeAmount(BigDecimal principal, List<FeeDefinitionDTO> fees) {
        if (fees == null) {
            return BigDecimal.ZERO;
        }
        return fees.stream()
                .filter(f -> "OPENING_FEE".equals(f.getType()))
                .findFirst()
                .map(f -> {
                    BigDecimal pct = f.getPercentage() != null ? f.getPercentage() : BigDecimal.ZERO;
                    BigDecimal fixed = f.getFixed() != null ? f.getFixed() : BigDecimal.ZERO;
                    return principal.multiply(pct, MC).divide(HUNDRED, MC).add(fixed, MC);
                })
                .orElse(BigDecimal.ZERO);
    }

    // ── TAE (annual percentage rate) ──────────────────────────────────────────

    /**
     * Computes the TAE (annual effective interest rate, percent) for the simulated loan.
     *
     * <p>When {@code openingFee == 0} the TAE collapses to {@code ((1+i)^12 - 1) * 100}.
     * Otherwise we solve the IRR of the borrower's cash flows via Newton-Raphson:
     * <pre>
     *   principal - openingFee = sum_{t=1..n} monthlyPayment / (1+r)^t
     * </pre>
     * The monthly rate {@code r} that zeroes that equation is then annualized.
     *
     * @param principal             requested principal
     * @param openingFee            opening fee amount (already computed)
     * @param monthlyPayment        monthly installment (full precision, NOT pre-rounded)
     * @param term                  number of months
     * @param monthlyRateGuess      starting guess (typically the nominal monthly rate)
     * @return TAE in percent (e.g. {@code 8.2929})
     */
    BigDecimal computeTae(BigDecimal principal, BigDecimal openingFee,
                          BigDecimal monthlyPayment, int term, BigDecimal monthlyRateGuess) {
        BigDecimal netPrincipal = principal.subtract(openingFee, MC);

        if (openingFee.signum() == 0) {
            return annualize(monthlyRateGuess);
        }

        BigDecimal r = monthlyRateGuess;
        for (int iter = 0; iter < NEWTON_MAX_ITERATIONS; iter++) {
            BigDecimal f = npv(netPrincipal, monthlyPayment, term, r);
            if (f.abs().compareTo(NEWTON_TOLERANCE) < 0) {
                break;
            }
            BigDecimal fPrime = npvDerivative(monthlyPayment, term, r);
            if (fPrime.signum() == 0) {
                break;
            }
            r = r.subtract(f.divide(fPrime, MC), MC);
            // Guard against runaway iterations producing nonsensical rates.
            if (r.compareTo(new BigDecimal("-0.99")) <= 0) {
                r = new BigDecimal("-0.99");
            }
        }

        return annualize(r);
    }

    /**
     * NPV of borrower cash flows given a monthly rate {@code r}.
     * {@code f(r) = -netPrincipal + sum_{t=1..n} monthlyPayment / (1+r)^t}
     * NPV == 0 when {@code r} is the IRR.
     */
    private BigDecimal npv(BigDecimal netPrincipal, BigDecimal monthlyPayment, int n, BigDecimal r) {
        BigDecimal onePlusR = ONE.add(r, MC);
        BigDecimal pvAnnuity;
        if (r.signum() == 0) {
            pvAnnuity = monthlyPayment.multiply(BigDecimal.valueOf(n), MC);
        } else {
            BigDecimal onePlusRPowN = onePlusR.pow(n, MC);
            BigDecimal factor = ONE.subtract(ONE.divide(onePlusRPowN, MC), MC).divide(r, MC);
            pvAnnuity = monthlyPayment.multiply(factor, MC);
        }
        return pvAnnuity.subtract(netPrincipal, MC);
    }

    /**
     * Derivative of {@link #npv} w.r.t. {@code r}, used for Newton-Raphson updates.
     * Implemented as a finite-difference approximation for numerical stability —
     * {@code MathContext.DECIMAL64} keeps the truncation error well within the
     * Newton convergence tolerance.
     */
    private BigDecimal npvDerivative(BigDecimal monthlyPayment, int n, BigDecimal r) {
        BigDecimal h = new BigDecimal("0.0000001"); // 1e-7
        BigDecimal fPlus = npvAnnuityOnly(monthlyPayment, n, r.add(h, MC));
        BigDecimal fMinus = npvAnnuityOnly(monthlyPayment, n, r.subtract(h, MC));
        return fPlus.subtract(fMinus, MC).divide(h.multiply(new BigDecimal("2"), MC), MC);
    }

    private BigDecimal npvAnnuityOnly(BigDecimal monthlyPayment, int n, BigDecimal r) {
        if (r.signum() == 0) {
            return monthlyPayment.multiply(BigDecimal.valueOf(n), MC);
        }
        BigDecimal onePlusR = ONE.add(r, MC);
        BigDecimal onePlusRPowN = onePlusR.pow(n, MC);
        BigDecimal factor = ONE.subtract(ONE.divide(onePlusRPowN, MC), MC).divide(r, MC);
        return monthlyPayment.multiply(factor, MC);
    }

    private BigDecimal annualize(BigDecimal monthlyRate) {
        BigDecimal compounded = ONE.add(monthlyRate, MC).pow(12, MC);
        return compounded.subtract(ONE, MC).multiply(HUNDRED, MC);
    }
}
