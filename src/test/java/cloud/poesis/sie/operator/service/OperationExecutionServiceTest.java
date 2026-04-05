package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.config.OperationSandboxConfig;
import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationExecutionServiceTest {

  private final List<EffectDto> dispatched = new ArrayList<>();

  private Object handleEffect(EffectDto record) {
    dispatched.add(record);
    if (record.closedLoop()) {
      return Map.of("approved", true, "score", 95);
    }
    return null;
  }

  @Test
  void executesSimpleFireAndForgetRule() throws Exception {
    String rule =
        """
                event = sys.receive("PaymentFailed")
                sys.effect("OrderUpdate", {"orderId": event["orderId"], "status": "failed"})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute(
            "mech-001",
            rule,
            Map.of("orderId", "ORD-123", "reason", "insufficient_funds"),
            this::handleEffect);

    assertThat(result.success()).isTrue();
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst().archetype()).isEqualTo("OrderUpdate");
    assertThat(result.effects().getFirst().data()).containsEntry("orderId", "ORD-123");
    assertThat(result.effects().getFirst().data()).containsEntry("status", "failed");
  }

  @Test
  void executesClosedLoopRule() throws Exception {
    String rule =
        """
                event = sys.receive("OrderCreated")
                result = sys.effect("ValidatePayment", {"orderId": event["orderId"]}).receive("ValidationResult")
                if result["approved"]:
                    sys.effect("OrderApproved", {"orderId": event["orderId"]})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-002", rule, Map.of("orderId", "ORD-456"), this::handleEffect);

    assertThat(result.success()).as("error: %s", result.error()).isTrue();
    assertThat(result.effects()).hasSize(2);
    assertThat(result.effects().get(0).closedLoop()).isTrue();
    assertThat(result.effects().get(1).archetype()).isEqualTo("OrderApproved");
  }

  @Test
  void hostFunctionNowIsAvailable() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                ts = now()
                sys.effect("Timestamped", {"ts": ts})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-003", rule, Map.of(), this::handleEffect);

    assertThat(result.success()).isTrue();
    String ts = (String) result.effects().getFirst().data().get("ts");
    assertThat(ts).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
  }

  @Test
  void hostFunctionUuid7IsAvailable() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                id = uuid7()
                sys.effect("WithId", {"id": id})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-004", rule, Map.of(), this::handleEffect);

    assertThat(result.success()).isTrue();
    String id = (String) result.effects().getFirst().data().get("id");
    assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  @Test
  void hostFunctionFullmatchIsAvailable() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                matched = fullmatch("[a-z]+", event["value"])
                sys.effect("Result", {"matched": matched})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-005", rule, Map.of("value", "abc"), this::handleEffect);

    assertThat(result.success()).isTrue();
    assertThat(result.effects().getFirst().data().get("matched")).isEqualTo(true);
  }

  @Test
  void hostFunctionSearchIsAvailable() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                ver = search("v([0-9.]+)", event["tag"])
                sys.effect("Result", {"version": ver})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-006", rule, Map.of("tag", "app-v2.3.1"), this::handleEffect);

    assertThat(result.success()).isTrue();
    assertThat(result.effects().getFirst().data().get("version")).isEqualTo("2.3.1");
  }

  @Test
  void syntaxErrorReturnsFailure() throws Exception {
    String rule = "event = sys.receive(\"T\")\nif True\n  pass";

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-err", rule, Map.of(), this::handleEffect);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).isNotEmpty();
  }

  @Test
  void runtimeErrorReturnsFailure() throws Exception {
    String rule =
        """
                event = sys.receive("T")
                x = 1 / 0
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-err2", rule, Map.of(), this::handleEffect);

    assertThat(result.success()).isFalse();
    assertThat(result.error()).containsIgnoringCase("division");
  }

  @Test
  void maxStepsEnforced() throws Exception {
    String rule =
        """
                event = sys.receive("T")
                x = 0
                for i in range(1000000):
                    x = x + 1
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox, 100);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-budget", rule, Map.of(), this::handleEffect);

    assertThat(result.success()).isFalse();
  }

  @Test
  void effectWithFullChain() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                result = (sys.effect("FraudCheck", {"orderId": event["oid"]})
                    .by("FraudGateway")
                    .receive("FraudResult")
                    .on("FraudReceiver"))
                if result["approved"]:
                    sys.effect("Cleared", {"oid": event["oid"]})
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute("mech-007", rule, Map.of("oid", "O-1"), this::handleEffect);

    assertThat(result.success()).isTrue();
    assertThat(result.effects()).hasSize(2);
    EffectDto fraud = result.effects().get(0);
    assertThat(fraud.effectorArchetype()).isEqualTo("FraudGateway");
    assertThat(fraud.feedbackArchetype()).isEqualTo("FraudResult");
    assertThat(fraud.feedbackReceptorArchetype()).isEqualTo("FraudReceiver");
  }

  @Test
  void containsRuntimeExceptionFromHandler() throws Exception {
    String rule =
        """
                event = sys.receive("Trigger")
                result = sys.effect("Bad", {"x": 1}).receive("Response")
                val = result["key"]
                """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute(
            "mech-err3",
            rule,
            Map.of(),
            effect -> {
              throw new IllegalStateException("Validation failed");
            });

    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("Validation failed");
  }
}
