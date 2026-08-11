# SIE Operator — Statement Definitions

GSM statement definitions for the SIE Operator's identity and protocol archetypes.

## Operator Identity

The Operator registers itself on the Definition Manager at startup using these statements:

| File                            | GSM Type  | Purpose                                                       |
| ------------------------------- | --------- | ------------------------------------------------------------- |
| `OperatorStructure.json`        | Structure | Declares the operator's existence (`purpose: "sie-operator"`) |
| `OperatorMechanism.json`        | Mechanism | Declares the `run-operation` function and its abstract rule   |
| `OperationRequest.schema.json`  | Archetype | Receptor data contract — shape of an operation trigger        |
| `OperationResponse.schema.json` | Archetype | Effector data contract — shape of an operation result         |

Client mechanisms declare **operability** by wiring an Effector → Interaction → the Operator's Receptor (typed by `OperationRequest`).

## Protocol Families (`protocol/`)

Each protocol defines a family of archetypes following the **Archetype Quad** pattern:

| Role            | Purpose                                          |
| --------------- | ------------------------------------------------ |
| **Data**        | Shape of payload flowing through the interaction |
| **Effector**    | Port archetype typing the emitting port          |
| **Receptor**    | Port archetype typing the receiving port         |
| **Interaction** | Archetype typing the causal link                 |

### Ownership split

- **Operator-internal** (this repo, `protocol/`): protocols intrinsic to the Operator runtime — in-process causal propagation and DM ↔ Operator governance event exchange. `$id` scheme: `gsmarc://ops/protocols/{family}/{Title}/v1`.
- **Application protocols** (ITIP, `gsm-frameworks/frameworks/{family}/`): protocols by which sourced applications interact with the world (HTTP, Kafka, AMQP, gRPC, GraphQL, JDBC, WebSocket). `$id` scheme: `gsmarc://gsm-frameworks/{family}/{Title}/v1`. The Operator vendors HTTP at build time via a Maven `<resource>` directive (see `pom.xml`).

### Implemented (operator-internal)

| Protocol                              | Boundary           | Description                                                        |
| ------------------------------------- | ------------------ | ------------------------------------------------------------------ |
| [`relay/`](protocol/relay/)           | In-memory (native) | Causal signal propagation between mechanisms in an operation chain |
| [`governance/`](protocol/governance/) | In-memory (native) | DM ↔ Operator governance event/result exchange                    |

### Implemented (ITIP-owned, vendored to operator)

| Protocol                                                                             | Boundary           | Description                                  |
| ------------------------------------------------------------------------------------ | ------------------ | -------------------------------------------- |
| [`gsm-frameworks/frameworks/http/`](../../../../gsm/gsm-frameworks/frameworks/http/) | Network (external) | HTTP request/response dispatch via WebClient |

The HTTP archetypes inherit the **framework base Archetypes** (`gsmarc://gsm-frameworks/Framework{SubjectType}/v1`), so
[`gsm-frameworks/frameworks/base/`](../../../../gsm/gsm-frameworks/frameworks/base/) is
vendored alongside them under `statement/base/` and registered first by `StructureSeedRunner`.

### Reserved (ITIP-owned, not yet implemented)

Kafka, AMQP, gRPC, GraphQL, JDBC, WebSocket — archetype quads to be authored under `gsm-frameworks/frameworks/{family}/` when first sourced.
