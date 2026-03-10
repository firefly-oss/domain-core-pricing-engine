# domain-core-pricing-engine

Core domain microservice for calculating prices, fees, and commissions using product configurations and a rule engine. This service acts as the centralized pricing orchestration layer within the Firefly platform, coordinating pricing computations, fee schedule management, and commission calculations through saga-based distributed transactions against the `core-common-pricing-mgmt` management service.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Setup](#setup)
- [API Endpoints](#api-endpoints)
- [SDK](#sdk)
- [Testing](#testing)

## Overview

The Core Pricing Engine service provides the domain orchestration layer for all pricing-related operations across the Firefly platform:

- **Price Calculation** -- Computes product prices by evaluating rate structures, tiered pricing models, and margin rules against product configurations.
- **Fee Management** -- Orchestrates fee structure definitions including fee components, application rules, and product-fee associations through multi-step saga workflows with compensating transactions.
- **Commission Calculation** -- Determines commission amounts based on configurable rule sets tied to product and distributor hierarchies.
- **Eligibility Evaluation** -- Evaluates applicant eligibility against published criteria (KYC/KYB, credit score, income, activity) and returns fit/not-fit determinations with reasons.
- **Saga Orchestration** -- All write operations are executed as saga workflows with compensating transactions, ensuring data consistency across distributed steps.

## Architecture

### Module Structure

| Module | Description |
|--------|-------------|
| `domain-core-pricing-engine-core` | Business logic: commands, handlers, saga workflows, service interfaces and implementations |
| `domain-core-pricing-engine-interfaces` | Interface adapters connecting core to infrastructure and external boundaries |
| `domain-core-pricing-engine-infra` | Infrastructure layer: API client factory, configuration properties, external service integration |
| `domain-core-pricing-engine-web` | Spring Boot WebFlux application: REST controllers, application entry point, configuration |
| `domain-core-pricing-engine-sdk` | Auto-generated client SDK from OpenAPI spec for downstream consumers |

### Tech Stack

- **Java 25**
- **Spring Boot** with **WebFlux** (reactive, non-blocking)
- **[FireflyFramework](https://github.com/fireflyframework/)** -- Parent POM (`firefly-parent`), and libraries:
  - `fireflyframework-web` -- Common web configurations
  - `fireflyframework-starter-domain` -- Domain layer CQRS and saga support
  - `fireflyframework-utils` -- Shared utilities
  - `fireflyframework-validators` -- Validation framework
- **FireflyFramework Transactional Saga Engine** -- `@Saga`, `@SagaStep`, `@StepEvent` annotations with `SagaEngine` for orchestrating distributed transactions with compensation
- **FireflyFramework CQRS** -- `CommandBus` for command dispatch
- **FireflyFramework EDA** -- Event-driven architecture with Kafka publishers for domain events
- **Project Reactor** (`Mono`/`Flux`) -- Reactive streams throughout
- **MapStruct** -- Object mapping between layers
- **Lombok** -- Boilerplate reduction
- **SpringDoc OpenAPI** -- API documentation and Swagger UI
- **Micrometer + Prometheus** -- Metrics export
- **Spring Boot Actuator** -- Health checks and operational endpoints
- **OpenAPI Generator** -- SDK generation from the OpenAPI spec (WebClient-based reactive client)

### Saga Workflows

| Saga | Steps | Description |
|------|-------|-------------|
| `RegisterPricingSaga` | `registerProductPricing` | Registers a new pricing configuration (rates, tiers, effective date) for a product |
| `UpdatePricingSaga` | `updatePricing` | Amends pricing by creating a new effective version with updated rates, margins, or tiers |
| `RegisterFeeSchemaSaga` | `registerFeeStructure` -> `registerFeeComponent` (depends on structure) -> `registerFeeApplicationRule` (depends on structure + component) -> `registerProductFeeStructure` (depends on structure) | Defines a complete fee scheme with compensating rollback for each step |
| `UpdateFeeRuleSaga` | `updateFee` | Updates a specific fee calculation rule |

### Domain Events

The service emits the following events via `@StepEvent`:

- `productPricing.registered`
- `pricing.updated`
- `feeStructure.registered`
- `feeComponent.registered`
- `feeApplicationRule.registered`
- `productFeeStructure.registered`
- `fee.updated`

## Setup

### Prerequisites

- **Java 25**
- **Maven 3.9+**
- Access to the FireflyFramework Maven repository for parent POM and BOM dependencies
- Running instance of `core-common-pricing-mgmt` service (or its API accessible at the configured base path)
- Apache Kafka broker (for domain event publishing)

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_ADDRESS` | `localhost` | Server bind address |
| `SERVER_PORT` | `8080` | Server port |
| `FIREFLY_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers for event publishing |

### Application Configuration

Key configuration sections in `application.yaml`:

```yaml
spring:
  application:
    name: domain-core-pricing-engine
    version: 1.0.0
    description: Core Pricing Engine - Domain Layer
    team:
      name: Firefly Software Solutions Inc
      email: dev@getfirefly.io
  threads:
    virtual:
      enabled: true

firefly:
  cqrs:
    enabled: true
    command:
      timeout: 30s
      metrics-enabled: true
      tracing-enabled: true
    query:
      timeout: 15s
      caching-enabled: true
      cache-ttl: 15m
  saga.performance.enabled: true
  eda:
    enabled: true
    default-publisher-type: KAFKA
  stepevents:
    enabled: true
```

Additional configuration typically includes:

- `api-configuration.common-platform.pricing-mgmt.base-path` -- Base URL for the downstream pricing management service
- Server port, logging levels, and profile-specific settings

### Build

```bash
mvn clean install
```

### Run

```bash
mvn -pl domain-core-pricing-engine-web spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar domain-core-pricing-engine-web/target/domain-core-pricing-engine.jar
```

## API Endpoints

### Pricing Controller

Base path: `/api/v1/pricing`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/pricing` | Register pricing -- create rates with tiers and effective-from date |
| `PUT` | `/api/v1/pricing/{pricingId}` | Amend pricing -- create a new effective version with updated rates, margins, or tiers |

### Fees Controller

Base path: `/api/v1/pricing/fees`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/pricing/fees/schemes` | Define fee scheme -- define fee types and calculation rules |
| `PUT` | `/api/v1/pricing/fees/schemes/{schemeId}/components/{componentId}` | Update fee rule -- update a specific fee calculation rule |

### Eligibility Controller

Base path: `/api/v1/pricing/eligibility`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/pricing/eligibility` | Publish eligibility criteria -- publish eligibility rules (KYC/KYB, score, income, activity) |
| `PATCH` | `/api/v1/pricing/eligibility/{eligibilityId}` | Adjust eligibility criteria -- adjust criteria with versioning |
| `POST` | `/api/v1/pricing/eligibility/{eligibilityId}/evaluate` | Evaluate eligibility -- evaluate applicant facts and return fit/not-fit with reasons |

### Common Headers

| Header | Required | Description |
|--------|----------|-------------|
| `X-Idempotency-Key` | No | Ensures identical requests are processed only once |
| `X-Party-ID` | Conditional | Client identifier (at least one identity header required) |
| `X-Employee-ID` | Conditional | Employee identifier |
| `X-Service-Account-ID` | Conditional | Service account identifier |
| `X-Auth-Roles` | No | Comma-separated roles (CUSTOMER, ADMIN, CUSTOMER_SUPPORT, SUPERVISOR, MANAGER, BRANCH_STAFF, SERVICE_ACCOUNT) |
| `X-Auth-Scopes` | No | Comma-separated OAuth2 scopes |
| `X-Request-ID` | No | Request traceability identifier |

## SDK

The `domain-core-pricing-engine-sdk` module auto-generates a reactive Java client from the service's OpenAPI specification using the OpenAPI Generator Maven plugin.

**Generated packages:**

| Package | Description |
|---------|-------------|
| `com.firefly.domain.core.pricing.engine.sdk.api` | API client classes for each controller tag |
| `com.firefly.domain.core.pricing.engine.sdk.model` | Request/response model DTOs |
| `com.firefly.domain.core.pricing.engine.sdk.invoker` | `ApiClient` configuration and HTTP transport |

**Usage:**

Add the SDK dependency to your consuming service:

```xml
<dependency>
    <groupId>com.firefly</groupId>
    <artifactId>domain-core-pricing-engine-sdk</artifactId>
    <version>${domain-core-pricing-engine.version}</version>
</dependency>
```

The SDK uses Spring WebClient for reactive HTTP calls. Configure the `ApiClient` base path in your `ClientFactory` to point at the running service instance.

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

Test dependencies include:

- **Spring Boot Test** -- `@SpringBootTest` for application context loading
- **Reactor Test** -- `StepVerifier` for reactive stream assertions

## Monitoring

The service exposes the following operational endpoints via Spring Boot Actuator:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Application health status |
| `/actuator/info` | Application information |
| `/actuator/prometheus` | Prometheus metrics endpoint |

OpenAPI documentation is available at:

- **Swagger UI**: [http://localhost:{port}/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **API Docs (JSON)**: [http://localhost:{port}/v3/api-docs](http://localhost:8080/v3/api-docs)

## Repository

[https://github.com/firefly-oss/domain-core-pricing-engine](https://github.com/firefly-oss/domain-core-pricing-engine)
