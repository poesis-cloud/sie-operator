# SIE Operator deployables

This folder contains ops/runtime assets for the Operator service.

- `ops/helm/`: Helm chart for Kubernetes deployments (dev/stage/prod depending cluster/context)

## Helm

Chart path:

- `ops/helm`

Install with defaults:

```bash
helm upgrade --install sie-operator \
  sie/sie-operator/ops/helm \
  -n sie --create-namespace
```

Environment values:

- `environments/dev/values.yaml`
- `environments/preprod/values.yaml`
- `environments/prod/values.yaml`

Each environment file is self-contained and carries the chart defaults for that target environment.
