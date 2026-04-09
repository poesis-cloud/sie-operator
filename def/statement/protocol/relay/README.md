# Relay Protocol

The Operator's **native causal propagation** protocol. Relay carries causal signals between mechanisms within an operation chain — one mechanism's effect becomes the next mechanism's cause, with no network boundary crossed.

## Archetype Quad

| File                           | Role        | Description                                                                             |
| ------------------------------ | ----------- | --------------------------------------------------------------------------------------- |
| `RelaySignal.schema.json`      | Data        | Causal signal: unconstrained `body` (extending archetypes constrain via `$ref`/`allOf`) |
| `RelayEffector.schema.json`    | Effector    | Port archetype — no addressing properties; targets resolved from Interaction graph      |
| `RelayReceptor.schema.json`    | Receptor    | Port archetype — no addressing properties; sources resolved from Interaction graph      |
| `RelayInteraction.schema.json` | Interaction | Causal link: in-memory propagation from upstream to downstream mechanism                |

## Design

- **No addressing**: Effector and Receptor have no target/source properties — causal wiring is resolved from the Interaction graph at runtime, enabling fan-out (one effector propagating to multiple downstream mechanisms).
- **In-memory**: No network boundary is crossed. The signal is handed off directly within the Operator process.
- **Unconstrained data**: `RelaySignal.body` is unconstrained at protocol level; extending archetypes constrain it to domain-specific shapes.

## Dispatcher

`MechanismRelayEffectorExecutionService` (`@Order(LOWEST_PRECEDENCE)`) — generic fallback dispatcher that accepts any effect with a non-null `effectorArchetype`. Returns the effect's data map directly as the reception (in-memory handoff).

## Name Origin

The name "relay" is sourced from three domains:

- **Athletics**: relay race — the baton (causal signal) passes from runner (mechanism) to runner, with strict handoff rules.
- **Electronics**: relay switch — a signal triggers the relay, which propagates it forward.
- **Networking**: SMTP relay, DNS relay — a message is forwarded through intermediary stations.
