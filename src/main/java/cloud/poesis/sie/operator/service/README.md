# Service Package — Reader's Guide

> **Package**: `cloud.poesis.sie.operator.service` > **7 files** (1 interface + 4 `@Service`/`@Component` beans + 2 dispatch implementations)
> managing mechanism topology resolution, Starlark rule execution, payload
> validation, and effect dispatching for the SIE Operator.

---

## 1. Package topology

The service package is organized in three dependency layers. Every
dependency arrow points **downward**; no lower layer depends on a higher one.

```
Layer 3 — Orchestration           OperationService (facade)
    │  delegates to
Layer 2 — Domain services         topology resolution, execution, validation
    │  consumes
Layer 1 — Effect dispatch         dispatch interface + ordered implementations
```

### Layer 3: Orchestration

`OperationService` is the **facade** that owns the full operation flow:
resolve topology → validate trigger payload → execute Starlark rule →
dispatch effects → validate closed-loop receptions → build response.
It injects all Layer 2 services and the ordered dispatch chain.

### Layer 2: Domain services

```
OperationTopologyResolutionService     resolves GSM topology from Definition Manager
OperationExecutionService              sandboxed Starlark rule execution
PayloadValidatorService                JSON Schema validation of trigger/effect payloads
```

### Layer 1: Effect dispatch

```
EffectDispatchService                  interface: supports(effect) + dispatch(effect)
├── HttpEffectDispatchService          @Order(0) — HTTP dispatch for effects with targetURI/method
└── LoggingEffectDispatchService       @Order (lowest) — fallback: logs effect, returns null
```

---

## 2. Dependency graph

```
OperationService (L3, facade)
│   consumes: OperationTopologyResolutionService (L2)
│             PayloadValidatorService (L2)
│             OperationExecutionService (L2)
│             List<EffectDispatchService> (L1, ordered)

OperationTopologyResolutionService (L2)
│   consumes: DefinitionManagerClient (client package)

OperationExecutionService (L2)
│   consumes: MechanismRuleApiConfig (config package) — Starlark host functions

PayloadValidatorService (L2)
│   consumes: (none — stateless, no-arg constructor)

HttpEffectDispatchService (L1)
│   consumes: WebClient.Builder (Spring-provided)

LoggingEffectDispatchService (L1)
│   consumes: (none — stateless)
```

No `@Lazy` annotations — no circular dependencies exist in this package.

---

## 3. Service roles

### 3.1 Layer 3 — Orchestration

| Service            | Role                                                                                                                                                |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OperationService` | Facade: topology resolution → trigger validation → Starlark execution → effect dispatch (with closed-loop reception validation) → response building |

### 3.2 Layer 2 — Domain services

| Service                              | Role                                                                                                                                              |
| ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OperationTopologyResolutionService` | Resolves full GSM topology for a Mechanism via Definition Manager: ports (receptors/effectors), data archetypes, and operability wiring check     |
| `OperationExecutionService`          | Sandboxed Starlark rule execution with step budget (`100_000`), `sys` module (receive/effect), and host functions (now, uuid7, fullmatch, search) |
| `PayloadValidatorService`            | Validates payloads against archetype JSON Schemas (trigger payloads on receptor archetypes, effect payloads on effector archetypes)               |

### 3.3 Layer 1 — Effect dispatch

| Service                        | `@Order` | Role                                                                                       |
| ------------------------------ | -------- | ------------------------------------------------------------------------------------------ |
| `EffectDispatchService`        | —        | Interface contract: `supports(EffectDto)` → boolean, `dispatch(EffectDto)` → reception map |
| `HttpEffectDispatchService`    | 0        | Dispatches effects containing `targetURI` + `method` via reactive `WebClient`              |
| `LoggingEffectDispatchService` | default  | Fallback dispatcher: accepts any effect, logs it, returns `null` (fire-and-forget)         |

The dispatch chain is `@Order`-sorted by Spring: `HttpEffectDispatchService`
is tried first; `LoggingEffectDispatchService` catches anything unmatched.

---

## 4. Operation flow (OperationService.operate)

```
1. Resolve topology        → OperationTopologyResolutionService.resolve(mechanismId)
2. Validate trigger        → PayloadValidatorService.validate(payload, receptorSchema)
3. Execute rule            → OperationExecutionService.execute(rule, sysModule)
4. For each effect:
   4a. Validate effect     → PayloadValidatorService.validate(data, effectorSchema)
   4b. Dispatch effect     → first EffectDispatchService.supports() match
   4c. If closed-loop:     → PayloadValidatorService.validate(reception, feedbackSchema)
5. Build response          → OperationResponseDto(success, effects, error)
```
