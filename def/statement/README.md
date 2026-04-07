# SIE Operator — Statement Definitions

GSM statement definitions for the SIE Operator's identity and protocol archetypes.

## Operator Identity

The Operator registers itself on the Definition Manager at startup using these statements:

| File                     | GSM Type  | Purpose                                                       |
| ------------------------ | --------- | ------------------------------------------------------------- |
| `OperatorStructure.json` | Structure | Declares the operator's existence (`purpose: "sie-operator"`) |
| `OperatorMechanism.json` | Mechanism | Declares the `run-operation` function and its abstract rule   |
| `OperationRequest.json`  | Archetype | Receptor data contract — shape of an operation trigger        |
| `OperationResponse.json` | Archetype | Effector data contract — shape of an operation result         |

Client mechanisms declare **operability** by wiring an Effector → Interaction → the Operator's Receptor (typed by `OperationRequest`).

## Protocol Families (`protocol/`)

Each protocol defines a family of archetypes following the **Archetype Quad** pattern:

| Role            | Purpose                                          |
| --------------- | ------------------------------------------------ |
| **Data**        | Shape of payload flowing through the interaction |
| **Effector**    | Port archetype typing the emitting port          |
| **Receptor**    | Port archetype typing the receiving port         |
| **Interaction** | Archetype typing the causal link                 |

### Implemented

| Protocol                    | Boundary           | Description                                                        |
| --------------------------- | ------------------ | ------------------------------------------------------------------ |
| [`http/`](protocol/http/)   | Network (external) | HTTP request/response dispatch via WebClient                       |
| [`relay/`](protocol/relay/) | In-memory (native) | Causal signal propagation between mechanisms in an operation chain |

### Reserved (not yet implemented)

| Protocol                            | Boundary           | Description                           |
| ----------------------------------- | ------------------ | ------------------------------------- |
| [`kafka/`](protocol/kafka/)         | Network (external) | Asynchronous event streaming          |
| [`amqp/`](protocol/amqp/)           | Network (external) | AMQP message broker dispatch          |
| [`grpc/`](protocol/grpc/)           | Network (external) | gRPC remote procedure call            |
| [`graphql/`](protocol/graphql/)     | Network (external) | GraphQL query/mutation dispatch       |
| [`jdbc/`](protocol/jdbc/)           | Network (external) | JDBC database query dispatch          |
| [`websocket/`](protocol/websocket/) | Network (external) | WebSocket bidirectional communication |
