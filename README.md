# SIE Causal Processor

Stateless Starlark Mechanism execution service for the Systemic Intelligence Engine (SIE).

## Purpose

The Causal Processor executes **Mechanisms** defined in GSM by interpreting their Starlark rule bodies. It fetches
Mechanism definitions (including Effectors, Receptors, and Interactions) from the Definition Manager, interprets the
Starlark code in a sandboxed environment, and dispatches the resulting effects to their targets via protocol-specific
calls (HTTP, Kafka, etc.) determined by the protocol archetype metadata on the wiring.

## Stack

- Java 21
- Spring Boot 3.5
- Starlark interpreter (`com.eed3si9n.starlark:starlark`)
- HTTP API (Spring Web)

## Build

```bash
mvn clean verify
```

## Run locally

```bash
make dev-up    # start dependencies (Definition Manager must be reachable)
make run-api   # start the causal processor
```

## Deploy

```bash
make prod-deploy DEPLOY_ENV=preprod IMAGE_REPOSITORY=<repo> IMAGE_TAG=<tag>
```
