# SIE Causal Processor deployables

This folder contains ops/runtime assets for the Causal Processor service.

- `ops/helm/`: Helm chart for Kubernetes deployments (dev/stage/prod depending cluster/context)

## Helm

Chart path:

- `ops/helm`

Install with defaults:

```bash
helm upgrade --install sie-causal-processor \
  sie/sie-causal-processor/ops/helm \
  -n sie --create-namespace
```

Environment values:

- `environments/dev/values.yaml`
- `environments/preprod/values.yaml`
- `environments/prod/values.yaml`

Each environment file is self-contained and carries the chart defaults for that target environment.
