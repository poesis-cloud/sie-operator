# SIE Operator

A lightweight **runtime engine** for executing GSM Mechanisms within the Systemic Intelligence Engine (SIE).

The Operator resolves Mechanism definitions from the Definition Manager, validates types against Archetype schemas, executes Starlark rules in a sandboxed environment, and dispatches effector calls to their targets via protocol-specific handlers (HTTP, Relay, Kafka).

---

## Architecture: A Mechanism Runtime Engine

The SIE Operator is architecturally analogous to a language runtime (JVM, CLR, BEAM). Instead of executing bytecode or script functions, it executes GSM **Mechanisms** — causal logic units whose behavior is defined by Starlark rules and whose I/O is typed by Archetype schemas.

### Runtime Concepts Glossary

The Operator's concepts are named after well-established runtime patterns:

| Runtime Concept                        | SIE Operator                  | Description                                                                                                                                                                                                                                                                                                                                    |
| -------------------------------------- | ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Frame** (JVM §2.6, WASM `callframe`) | **Operation Frame**           | Resolved execution context for a single Mechanism: the Mechanism itself, its Ports (Effectors/Receptors), data Archetypes, and active Interactions. Created per invocation, discarded after completion.                                                                                                                                        |
| **Linker / Symbol Resolver**           | **Frame Resolution**          | Queries the Definition Manager to discover and validate a Mechanism's ports, archetypes, and interaction wiring — analogous to dynamic linking resolving symbolic references into concrete bindings.                                                                                                                                           |
| **Type System**                        | **Archetype**                 | JSON Schema documents (draft 2020-12+) attached to Archetypes — define the shape and constraints of data flowing through ports.                                                                                                                                                                                                                |
| **Type Checking**                      | **Input/Output Validation**   | Validates operation inputs against receptor's output's Archetype schemas and effect outputs against effector's output's Archetype schemas (JSON Schema v7).                                                                                                                                                                                    |
| **Evaluation / Interpretation**        | **Rule Interpretation**       | Starlark sandbox interprets the Mechanism's rule source code with the `sys` API surface. `sys.effect()` calls execute inline — Starlark control flow (`if`/`else`, loops) determines which effectors are emitted. Capped at 100k Starlark steps.                                                                                               |
| **Syscall / FFI / P/Invoke**           | **Effector Dispatch**         | Effector handlers that execute calls against targets via protocol-specific dispatchers (HTTP, Kafka, Relay). Each protocol defines its own archetype quad (Data + Effector + Receptor + Interaction) and dispatcher, all sharing the same SPI. Protocols differ only in transport: HTTP/Kafka cross network boundaries; Relay stays in-memory. |
| **Host Functions**                     | **Sandbox Host Functions**    | Built-in functions provided by the runtime to the sandboxed Starlark environment: `now()`, `uuid7()`, `fullmatch()`, `search()`.                                                                                                                                                                                                               |
| **Call Chain**                         | **Operation Chain** (planned) | Ordered sequence of Operation Frames linked by Relay Interactions, resolved via topological sort, executed in causal order.                                                                                                                                                                                                                    |
| **Sandbox**                            | **Sandbox**                   | Isolated Starlark execution environment — one fresh sandbox per rule evaluation, no shared mutable state between invocations.                                                                                                                                                                                                                  |

---

## Execution Pipeline

A single operation request traverses five phases:

```
POST /api/v1/operations { mechanismAscriptionId, operationInput }
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  1. FRAME RESOLUTION                                        │
│                                                             │
│  Resolve the target Mechanism from Definition Manager.      │
│  Discover its Receptor and Effector ports.                  │
│  Resolve data Archetypes (JSON Schemas) for each port.      │
│  Validate operability: at least one effector must have an   │
│  active Interaction wired to the Operator's own receptor.   │
│  Validate executable status: ACTIVE or DEPRECATED only.     │
│  Output: OperationFrame (mechanism + ports + archetypes)    │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  2. TYPE CHECKING (Input Validation)                        │
│                                                             │
│  Validate the operationInput payload against each receptor  │
│  port's data Archetype schema (JSON Schema v7).             │
│  Reject with 422 if validation fails.                       │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  3. RULE EVALUATION (Starlark Sandbox)                      │
│                                                             │
│  Create a fresh Starlark sandbox with the sys module.       │
│  Wrap the Mechanism's rule in a _rule() function.           │
│  Execute via Starlark.execFile() with 100k step limit.      │
│  Rule uses sys.receive() for input, sys.effect() for output.│
│  Collect effects declared during execution.                 │
│  Handle: syntax errors, eval errors, step limit exceeded.   │
│  Output: ExecutionResult { success, effects[], error }      │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  4. EFFECTOR DISPATCH                                       │
│                                                             │
│  For each effector call produced by the rule:               │
│  ├─ Match to a dispatcher (handler) via the typing          │
│  │  archetype on the effector port.                         │
│  │                                                          │
│  │  Protocol handlers:                                      │
│  ├─ HTTP: sends request via WebClient,                      │
│  │  returns { statusCode, contentType, body }.              │
│  ├─ Relay: in-memory handoff to next mechanism in the       │
│  │  operation chain, no network I/O.                        │
│  ├─ Kafka: reserved, not yet implemented.                   │
│  │                                                          │
│  If closed-loop feedback declared: validate response        │
│  against feedback receptor archetype schema.                │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│  5. RESPONSE                                                │
│                                                             │
│  Return OperationResponse { success, effects[], error }     │
│  HTTP 200 on success, HTTP 422 on topology/validation       │
│  failures, HTTP 500 on unexpected errors.                   │
└─────────────────────────────────────────────────────────────┘
```

---

## Effector Dispatch System

Effectors are the Operator's **calls** — each effector produced by a rule evaluation represents an outbound call that the Operator must dispatch. Dispatchers are matched by the **typing archetype** on the effector port.

All protocols are **structurally identical**: same dispatcher SPI (`MechanismEffectorExecutionService`), same `supports()` / `dispatch()` contract, same archetype quad (Data + Effector + Receptor + Interaction). Protocols differ only in transport.

**Ownership split** (decided during the GSM Sourcer / SBAS design phase):

- **Operator-internal protocols** live in this repo under `def/statement/protocol/` — Relay (in-memory causal propagation) and Governance (DM ↔ Operator events). These are part of the Operator runtime contract.
- **Application protocols** (HTTP, Kafka, AMQP, gRPC, GraphQL, JDBC, WebSocket) are owned by **ITIP** under `itip/itip-frameworks/frameworks/{family}/` (the standalone, public `itip-frameworks` repo). They describe how applications interact with the world and are catalogued alongside other ITIP frameworks (TOGAF, ISO 25000, GDPR…). The Operator vendors HTTP at build time via a Maven `<resource>` directive pointing to ITIP, so `StructureSeedRunner` can still register them at startup.

| Protocol                                        | Owner        | Boundary           | Analogy            | Description                                                                                           |
| ----------------------------------------------- | ------------ | ------------------ | ------------------ | ----------------------------------------------------------------------------------------------------- |
| **HTTP**                                        | ITIP         | Network (external) | Syscall / FFI      | Crosses the runtime boundary to reach external targets via HTTP requests/responses.                   |
| **Relay**                                       | sie-operator | In-memory (native) | Causal propagation | Propagates causal signals between mechanisms within an operation chain — no network boundary crossed. |
| **Governance**                                  | sie-operator | In-memory (native) | DM ↔ Operator     | Governance event/result exchange between Definition Manager and Operator.                             |
| **Kafka, AMQP, gRPC, GraphQL, JDBC, WebSocket** | ITIP         | Network (external) | Various            | Reserved framework directories; archetype quads to be authored when first sourced.                    |

### Archetype Quad Pattern

Each protocol defines a family of 4 Archetypes (following the GSM Effector/Receptor/Interaction/Data pattern):

| Role            | Purpose                                          | Example (HTTP)                | Example (Relay)    |
| --------------- | ------------------------------------------------ | ----------------------------- | ------------------ |
| **Data**        | Shape of payload flowing through the interaction | `HttpRequest`, `HttpResponse` | `RelaySignal`      |
| **Effector**    | Port archetype typing the emitting port          | `HttpRequestEffector`         | `RelayEffector`    |
| **Receptor**    | Port archetype typing the receiving port         | `HttpRequestReceptor`         | `RelayReceptor`    |
| **Interaction** | Archetype typing the causal link                 | `HttpRequestInteraction`      | `RelayInteraction` |

### Protocols

#### HTTP (`itip/itip-frameworks/frameworks/http/` — vendored to operator classpath)

Crosses the network boundary — analogous to a **syscall** or **FFI call**. `$id` scheme: `gsmarc://itip/frameworks/http/{Title}/v1`.

Two directions:

- **Request** (outbound): Operator sends an HTTP request to an external target.
  - Effector identity-bound by `method` + `targetUri` (RFC 9110 methods: GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS).
  - Receptor identity-bound by `targetUri` pattern.
  - Data: `method`, `targetUri` (URI template), `contentType`, `accept`, `body`.
- **Response** (inbound): Operator receives an HTTP response.
  - Data: `statusCode` (100–599), `contentType`, `body`.

#### Relay (`protocol/relay/`)

The Operator's **native causal propagation** protocol. Relay carries causal signals between mechanisms within an operation chain — one mechanism's effect becomes the next mechanism's cause, with no network boundary crossed.

Relay enables **operation chains**: sequences of mechanisms executed within a single operation request. When a mechanism's effector is wired via a RelayInteraction to downstream receptor(s), the Operator propagates the causal signal in-memory to the next mechanism(s) in the chain.

- Effector and Receptor have no addressing properties — causal wiring is resolved from the Interaction graph at runtime, enabling fan-out (one effector propagating to multiple downstream mechanisms).
- Data (`RelaySignal`): unconstrained `body` (extending archetypes constrain via `$ref`/`allOf`).

The name "relay" is sourced from three domains:

- **Athletics**: relay race — the baton (causal signal) passes from runner (mechanism) to runner, with strict handoff rules.
- **Electronics**: relay switch — a signal triggers the relay, which propagates it forward.
- **Networking**: SMTP relay, DNS relay — a message is forwarded through intermediary stations.

#### Kafka and other application protocols (reserved)

Kafka, AMQP, gRPC, GraphQL, JDBC, WebSocket archetype families are reserved under `itip/itip-frameworks/frameworks/{family}/`. Not yet implemented.

---

## Starlark Sandbox API

The `sys` namespace is the single API surface available to Mechanism rules. The sandbox is created fresh per invocation — no shared mutable state.

### Core API

```python
# Receive trigger input (MUST be first executable statement)
payload = sys.receive("ArchetypeName")

# Emit a fire-and-forget effect
sys.effect("ArchetypeName", {"key": "value"})

# Emit a closed-loop effect (with feedback)
sys.effect("EffectorArchetype", {"key": "value"}) \
   .by("EffectorPortName") \
   .receive("FeedbackArchetype") \
   .on("FeedbackReceptorName")
```

### Host Functions

| Function                     | Signature          | Description                              |
| ---------------------------- | ------------------ | ---------------------------------------- |
| `now()`                      | `→ string`         | Current UTC timestamp (ISO 8601)         |
| `uuid7()`                    | `→ string`         | UUIDv7 (time-sorted unique ID)           |
| `fullmatch(pattern, string)` | `→ bool`           | Regex full match (Python `re.fullmatch`) |
| `search(pattern, string)`    | `→ string \| None` | Regex search, returns first match group  |

### Type Conversion

| Java                           | Starlark        |
| ------------------------------ | --------------- |
| `Map<String, Object>`          | `Dict`          |
| `List<Object>`                 | `StarlarkList`  |
| `null`                         | `NONE`          |
| `String`, `Integer`, `Boolean` | native literals |

### Execution Limits

- **Max steps**: 100,000 Starlark execution steps per invocation (configurable).
- **Interruption**: sandbox supports cooperative interruption.
- **Isolation**: each invocation gets a fresh sandbox — no leaking state between executions.

---

## Bootstrap Process

At startup, the Operator idempotently registers its own identity with the Definition Manager:

```
1. Resolve base GSM Archetypes (Structure, Mechanism, Effector, Receptor)
2. Create OperationRequest data Archetype  → typing for operator's receptor port
3. Create OperationResponse data Archetype → typing for operator's effector port
4. Create operator Structure               → { purpose: "sie-operator" }
5. Create operator Mechanism               → { function: "run-operation", rule: <Starlark> }
6. Create operator Receptor                → typed by OperationRequest archetype
7. Create operator Effector                → typed by OperationResponse archetype
```

All steps are idempotent: existing ascriptions are found by query before attempting creation. Controlled by `op.bootstrap.enabled` (default: `true`).

---

## Validation Layers

The Operator validates at four levels, each corresponding to a phase in the execution pipeline:

| Layer              | Phase             | What                                                                                                        | Failure                      |
| ------------------ | ----------------- | ----------------------------------------------------------------------------------------------------------- | ---------------------------- |
| **Operability**    | Frame Resolution  | Mechanism exists, is executable (ACTIVE/DEPRECATED), and is wired to operator's receptor via an Interaction | 422 ProblemDetail            |
| **Input Types**    | Type Checking     | Operation input conforms to receptor archetype schema                                                       | 422 ProblemDetail            |
| **Output Types**   | Effector Dispatch | Effector data conforms to effector archetype schema (implicit via sandbox)                                  | Execution error              |
| **Feedback Types** | Effector Dispatch | Closed-loop response conforms to feedback receptor archetype schema                                         | Validation error in response |

---

## Project Structure

```
sie-operator/
├── def/statement/                      # GSM statement definitions
│   ├── OperationRequest.schema.json     # Receptor data archetype (operator input contract)
│   ├── OperationResponse.schema.json   # Effector data archetype (operator output contract)
│   ├── OperatorMechanism.json          # Mechanism statement (Starlark rule)
│   ├── OperatorStructure.json          # Structure statement (purpose: sie-operator)
│   └── protocol/                       # Operator-internal protocols ONLY
│       ├── relay/                      # Relay protocol archetype family (4 files)
│       │   ├── RelaySignal.schema.json          # Data: causal signal (unconstrained body)
│       │   ├── RelayEffector.schema.json        # Effector: causal signal emission
│       │   ├── RelayReceptor.schema.json        # Receptor: causal signal reception
│       │   └── RelayInteraction.schema.json     # Interaction: causal propagation link
│       └── governance/                 # Governance event/result archetype family (8 files)
│
│   # NOTE: Application protocols (HTTP, Kafka, AMQP, gRPC, GraphQL, JDBC,
│   # WebSocket) are owned by ITIP — see itip/itip-frameworks/frameworks/{family}/.
│   # HTTP is vendored to the operator classpath via a Maven <resource>
│   # directive in pom.xml so StructureSeedRunner can register it at startup.
│
├── src/main/java/cloud/poesis/sie/operator/
│   ├── SieOperatorApplication.java     # Spring Boot entry point
│   ├── bootstrap/
│   │   └── StructureSeedRunner.java    # Bootstrap: register operator identity
│   ├── client/
│   │   └── DefinitionManagerClient.java # REST client to Definition Manager
│   ├── config/
│   │   ├── OpenApiConfig.java          # Swagger/OpenAPI metadata
│   │   ├── OperationSandboxConfig.java # Starlark sandbox: sys module, host functions, types
│   │   ├── SecurityConfig.java         # OAuth2 JWT / dev-mode open access
│   │   └── WebClientConfig.java        # WebClient for Definition Manager
│   ├── controller/
│   │   └── OperationController.java    # POST /api/v1/operations
│   ├── dto/
│   │   ├── ArchetypeAscriptionDto.java # Archetype: id, title, schema
│   │   ├── EffectDto.java              # Effector call: archetype, data, feedback config
│   │   ├── EffectorAscriptionDto.java  # Effector port: mechanism, archetype
│   │   ├── InteractionAscriptionDto.java # Interaction: effector → receptor
│   │   ├── MechanismAscriptionDto.java # Mechanism: structure, function, rule source
│   │   ├── OperationRequestDto.java    # API input: mechanismAscriptionId, operationInput
│   │   ├── OperationResponseDto.java   # API output: success, effects[], error
│   │   ├── OperationFrameDto.java      # Operation Frame: mechanism + ports + archetypes
│   │   ├── ReceptorAscriptionDto.java  # Receptor port: mechanism, archetype
│   │   └── StructureAscriptionDto.java # Structure: purpose
│   ├── exception/
│   │   └── OperationFrameResolutionException.java   # Frame resolution failure
│   └── service/
│       ├── MechanismEffectorExecutionService.java     # Effector dispatcher interface (SPI)
│       ├── MechanismHttpEffectorExecutionService.java # HTTP protocol effector handler
│       ├── MechanismRelayEffectorExecutionService.java # Relay protocol effector handler
│       ├── OperationExecutionService.java             # Starlark rule evaluator
│       ├── OperationInputValidationService.java       # JSON Schema type checker
│       ├── OperationService.java                      # Orchestrator (pipeline)
│       └── OperationFrameResolutionService.java       # Frame resolver
│
├── src/test/java/...                   # Unit + integration tests (143 + 3)
├── ops/helm/                           # Helm chart (dev/preprod/prod)
├── Dockerfile                          # Multi-stage build (Maven → JRE 21)
├── Makefile                            # dev-up/dev-down/run-api/prod-deploy
└── pom.xml                             # Spring Boot 3.5.13, Java 21
```

---

## Stack

| Component         | Technology                                     | Version |
| ----------------- | ---------------------------------------------- | ------- |
| Language          | Java                                           | 21      |
| Framework         | Spring Boot                                    | 3.5.13  |
| Rule Engine       | Starlark (com.eed3si9n.starlark)               | 4.2.1   |
| Schema Validation | json-schema-validator (com.networknt)          | 1.5.7   |
| API Docs          | springdoc-openapi                              | 2.8.16  |
| HTTP Client       | Spring WebClient (Reactor Netty)               | —       |
| Security          | Spring Security + OAuth2 Resource Server (JWT) | —       |
| Observability     | Micrometer → OpenTelemetry OTLP                | —       |
| Coverage          | JaCoCo (≥95% instruction, BUNDLE level)        | 0.8.14  |
| Static Analysis   | SpotBugs                                       | 4.9.8.2 |
| Formatting        | Spotless (Google Java Format + Prettier)       | 3.4.0   |

---

## Configuration

```yaml
server:
  port: ${SERVER_PORT:8081}

op:
  definition-manager:
    url: ${DEFINITION_MANAGER_URL:http://localhost:8080} # Definition Manager base URL
  security:
    oauth2-login-enabled: ${OP_OAUTH2_LOGIN_ENABLED:false} # false = dev mode (open access)
  bootstrap:
    enabled: true # Register operator identity on startup

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI:http://localhost:9000/realms/sie}
```

### Actuator Endpoints

| Endpoint               | Purpose                           |
| ---------------------- | --------------------------------- |
| `/actuator/health`     | Liveness/readiness probe          |
| `/actuator/info`       | Application metadata              |
| `/actuator/prometheus` | Metrics (Micrometer → Prometheus) |

---

## Build & Test

```bash
mvn clean verify          # Full build: compile + test + JaCoCo + SpotBugs + Spotless
mvn test                  # Unit tests only (143 tests)
mvn failsafe:integration-test  # Integration tests (3 tests, MockWebServer)
mvn spotless:apply        # Auto-format before committing
```

Coverage gate: **≥95% instruction coverage** at BUNDLE level (currently ~98%).

## Run Locally

```bash
make dev-up               # Deploy dependencies to local Kubernetes (namespace: sie)
make run-api              # Start operator (requires DEFINITION_MANAGER_URL)
make dev-down             # Teardown
```

## Deploy

```bash
make prod-deploy DEPLOY_ENV=preprod IMAGE_REPOSITORY=<repo> IMAGE_TAG=<tag>
make package-helm         # Package Helm chart to .tgz
```

---

## API

### POST /api/v1/operations

Execute a Mechanism.

**Request:**

```json
{
  "mechanismAscriptionId": "019458a3-7b2e-7000-8000-000000000001",
  "operationInput": {
    "key": "value"
  }
}
```

**Response (success):**

```json
{
  "success": true,
  "effects": [
    {
      "archetype": "HttpRequest",
      "data": { "method": "POST", "targetUri": "https://..." },
      "effectorArchetype": "HttpRequestEffector",
      "closedLoop": false
    }
  ],
  "error": null
}
```

**Response (failure):**

```json
{
  "success": false,
  "effects": [],
  "error": "Rule execution failed: ..."
}
```

### OpenAPI

Available at `/api-docs` (JSON) and `/swagger-ui.html` (UI) when the application is running.
