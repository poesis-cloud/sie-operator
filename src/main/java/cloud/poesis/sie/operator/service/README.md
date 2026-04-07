# Service Package — Reader's Guide

> **Package**: `cloud.poesis.sie.operator.service` — **7 files** (1 interface + 4 `@Service`/`@Component` beans + 2 dispatch implementations)
> managing mechanism frame resolution, Starlark rule execution, input
> validation, and effector dispatching for the SIE Operator.

---

## 1. Package structure

The service package is organized in three dependency layers. Every
dependency arrow points **downward**; no lower layer depends on a higher one.

```
Layer 3 — Orchestration           OperationService (facade)
    │  delegates to
Layer 2 — Domain services         frame resolution, execution, validation
    │  consumes
Layer 1 — Effector dispatch       dispatch interface + ordered implementations
```

### Layer 3: Orchestration

`OperationService` is the **facade** that owns the full operation flow:
resolve frame → validate trigger input → execute Starlark rule →
dispatch effects → validate closed-loop receptions → build response.
It injects all Layer 2 services and the ordered dispatch chain.

### Layer 2: Domain services

```
OperationFrameResolutionService        resolves GSM operation frame from Definition Manager
OperationExecutionService              sandboxed Starlark rule execution
OperationInputValidationService        JSON Schema validation of trigger inputs and effect outputs
```

### Layer 1: Effector dispatch

```
MechanismEffectorExecutionService      interface: supports(effect) + dispatch(effect)
├── MechanismHttpEffectorExecutionService   @Order(0) — HTTP dispatch for effects with targetUri/method
└── MechanismRelayEffectorExecutionService  @Order(LOWEST_PRECEDENCE) — fallback: in-memory relay
```

---

## 2. Dependency graph

```
OperationService (L3, facade)
│   consumes: OperationFrameResolutionService (L2)
│             OperationInputValidationService (L2)
│             OperationExecutionService (L2)
│             List<MechanismEffectorExecutionService> (L1, ordered)

OperationFrameResolutionService (L2)
│   consumes: DefinitionManagerClient (client package)

OperationExecutionService (L2)
│   consumes: SandboxFactory (functional interface from OperationSandboxConfig)

OperationInputValidationService (L2)
│   consumes: (none — stateless, no-arg constructor)

MechanismHttpEffectorExecutionService (L1)
│   consumes: WebClient.Builder (Spring-provided)

MechanismRelayEffectorExecutionService (L1)
│   consumes: (none — stateless)
```

No `@Lazy` annotations — no circular dependencies exist in this package.

---

## 3. Service roles

### 3.1 Layer 3 — Orchestration

| Service            | Role                                                                                                                                               |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OperationService` | Facade: frame resolution → trigger validation → Starlark execution → effector dispatch (with closed-loop reception validation) → response building |

### 3.2 Layer 2 — Domain services

| Service                           | Role                                                                                                                                                 |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `OperationFrameResolutionService` | Resolves full GSM operation frame for a Mechanism via Definition Manager: ports (receptors/effectors), data archetypes, and operability wiring check |
| `OperationExecutionService`       | Sandboxed Starlark rule execution with step budget (`100_000`), `sys` module (receive/effect), and host functions (now, uuid7, fullmatch, search)    |
| `OperationInputValidationService` | Validates inputs against archetype JSON Schemas (trigger inputs on receptor archetypes, closed-loop responses on feedback archetypes)                |

### 3.3 Layer 1 — Effector dispatch

| Service                                  | `@Order`          | Role                                                                                                    |
| ---------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------- |
| `MechanismEffectorExecutionService`      | —                 | Interface contract: `supports(EffectDto)` → boolean, `dispatch(EffectDto)` → reception map              |
| `MechanismHttpEffectorExecutionService`  | 0                 | Dispatches effects containing `targetUri` + `method` via reactive `WebClient`                           |
| `MechanismRelayEffectorExecutionService` | LOWEST_PRECEDENCE | Generic fallback: accepts any effect with a non-null effectorArchetype, relays data as in-memory signal |

The dispatch chain is `@Order`-sorted by Spring: `MechanismHttpEffectorExecutionService`
is tried first; `MechanismRelayEffectorExecutionService` catches anything unmatched.

---

## 4. Operation flow (OperationService.operate)

```
1. Resolve frame           → OperationFrameResolutionService.resolve(mechanismId)
2. Validate trigger        → OperationInputValidationService.validate(input, receptorSchema)
3. Execute rule            → OperationExecutionService.execute(rule, sysModule)
4. For each effect:
   4a. Dispatch effect     → first MechanismEffectorExecutionService.supports() match
   4b. If closed-loop:     → OperationInputValidationService.validate(reception, feedbackSchema)
5. Build response          → OperationResponseDto(success, effects, error)
```
