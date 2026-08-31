# Governance Protocol

Governance protocol statements for the ITIP meta-governance ontology.

## Overview

The governance protocol defines how governance events and results flow through the SIE Operator's governance fabric. It follows the standard GSM protocol quad pattern (Data → Effector → Receptor → Interaction).

## Protocol Quads

### GovernanceEvent (inbound)

| Statement                    | GSM Primitive    | Purpose                                                                                      |
| ---------------------------- | ---------------- | -------------------------------------------------------------------------------------------- |
| `GovernanceEvent`            | Archetype (data) | Governance event payload: evaluation triggers, compliance assertions, sourcing notifications |
| `GovernanceEventEffector`    | Effector         | Emits governance events; identity-bound by `eventType` + `subjectRef`                        |
| `GovernanceEventReceptor`    | Receptor         | Receives governance events; identity-bound by `ontologyRef`                                  |
| `GovernanceEventInteraction` | Interaction      | Causal link: event emitter → ontology listener                                               |

### GovernanceResult (outbound)

| Statement                     | GSM Primitive    | Purpose                                                                             |
| ----------------------------- | ---------------- | ----------------------------------------------------------------------------------- |
| `GovernanceResult`            | Archetype (data) | Governance result payload: evaluation/compliance/sourcing outcomes (PASS/WARN/FAIL) |
| `GovernanceResultEffector`    | Effector         | Emits governance results; identity-bound by `resultType`                            |
| `GovernanceResultReceptor`    | Receptor         | Receives governance results; identity-bound by `ontologyRef`                        |
| `GovernanceResultInteraction` | Interaction      | Causal link: evaluation engine → governance consumer                                |

## Event Types

| Event Type              | Description                                                                     |
| ----------------------- | ------------------------------------------------------------------------------- |
| `EVALUATION_TRIGGER`    | Signals that an evaluation cycle should be initiated for the referenced subject |
| `COMPLIANCE_ASSERTION`  | Asserts a compliance posture change for a governed entity                       |
| `SOURCING_NOTIFICATION` | Notifies that an ontology sourcing operation completed                          |

## Result Types

| Result Type         | Description                                                           |
| ------------------- | --------------------------------------------------------------------- |
| `EVALUATION_RESULT` | Outcome of an evaluation cycle (rule execution against a subject)     |
| `COMPLIANCE_RESULT` | Aggregated compliance posture for a governed entity under an ontology |
| `SOURCING_RESULT`   | Outcome of a ontology sourcing operation                              |
| `SOURCING_RESULT`   | Outcome of an ontology sourcing operation                             |

## URI Pattern

All schema `$id` values follow: `gsmarc://ops/protocols/governance/{ConceptName}/v1`
