SHELL := /bin/bash
.EXPORT_ALL_VARIABLES:

.PHONY: dev-check dev-up dev-down deploy-check prod-deploy package-helm run-api test verify ensure-runtime

-include .env
-include .env.dev

NAMESPACE ?= sie
DEPLOY_ENV ?= preprod

OP_CHART ?= ./ops/helm
OP_ENV_FILE ?= $(OP_CHART)/environments/$(DEPLOY_ENV)/values.yaml

define kill_port_listener
	@ss -ltnp '( sport = :$(1) )' 2>/dev/null | awk -F'pid=' '/kubectl/ {split($$2, parts, /[,)]/); print parts[1]}' | xargs -r kill
endef

ensure-runtime:
	@if [[ -n "$$XDG_RUNTIME_DIR" ]] && [[ ! -d "$$XDG_RUNTIME_DIR" ]]; then \
		echo "Creating missing XDG_RUNTIME_DIR ($$XDG_RUNTIME_DIR)..."; \
		sudo mkdir -p "$$XDG_RUNTIME_DIR" && sudo chown $$(id -u):$$(id -g) "$$XDG_RUNTIME_DIR" && chmod 700 "$$XDG_RUNTIME_DIR"; \
	fi

dev-check:
	@command -v kubectl >/dev/null 2>&1 || { echo "Missing required command: kubectl"; exit 1; }
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@kubectl config current-context >/dev/null 2>&1 || { echo "No active Kubernetes context. Configure kubeconfig first."; exit 1; }
	@kubectl get ns >/dev/null 2>&1 || { echo "Cannot reach Kubernetes API with current context."; exit 1; }
	@test -d "$(OP_CHART)" || { echo "Missing chart directory: $(OP_CHART)"; exit 1; }
	@test -f "$(OP_CHART)/environments/dev/values.yaml" || { echo "Missing dev values file: $(OP_CHART)/environments/dev/values.yaml"; exit 1; }
	@echo "dev-check passed"

deploy-check:
	@command -v kubectl >/dev/null 2>&1 || { echo "Missing required command: kubectl"; exit 1; }
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@kubectl config current-context >/dev/null 2>&1 || { echo "No active Kubernetes context. Configure kubeconfig first."; exit 1; }
	@kubectl get ns >/dev/null 2>&1 || { echo "Cannot reach Kubernetes API with current context."; exit 1; }
	@test -d "$(OP_CHART)" || { echo "Missing chart directory: $(OP_CHART)"; exit 1; }
	@test -f "$(OP_ENV_FILE)" || { echo "Missing environment values file: $(OP_ENV_FILE)"; exit 1; }
	@: "$${IMAGE_REPOSITORY:?Missing IMAGE_REPOSITORY in environment}"
	@: "$${IMAGE_TAG:?Missing IMAGE_TAG in environment}"
	@echo "deploy-check passed"

dev-up:
	@$(MAKE) ensure-runtime
	kubectl get ns $(NAMESPACE) >/dev/null 2>&1 || kubectl create ns $(NAMESPACE) >/dev/null
	helm upgrade --install sie-operator $(OP_CHART) -n $(NAMESPACE) --create-namespace --wait --timeout 5m0s \
		-f $(OP_CHART)/environments/dev/values.yaml
	@echo "Operator deployed to dev."

dev-down:
	@PID=$$(lsof -ti :8081 2>/dev/null); \
	if [[ -n "$$PID" ]]; then \
		kill $$PID 2>/dev/null || true; \
		echo "Stopped process on port 8081 (PID $$PID)"; \
	fi
	helm uninstall sie-operator -n $(NAMESPACE) || true

run-api:
	DEFINITION_MANAGER_URL="$(DEFINITION_MANAGER_URL)" \
	mvn spring-boot:run

prod-deploy:
	@$(MAKE) deploy-check
	helm upgrade --install sie-operator $(OP_CHART) -n $(NAMESPACE) --create-namespace --wait --timeout 10m0s \
		-f $(OP_ENV_FILE) \
		--set image.repository="$${IMAGE_REPOSITORY}" \
		--set image.tag="$${IMAGE_TAG}"

package-helm:
	@command -v helm >/dev/null 2>&1 || { echo "Missing required command: helm"; exit 1; }
	@test -d "$(OP_CHART)" || { echo "Missing chart directory: $(OP_CHART)"; exit 1; }
	helm package $(OP_CHART)

test:
	mvn test

verify:
	mvn verify
