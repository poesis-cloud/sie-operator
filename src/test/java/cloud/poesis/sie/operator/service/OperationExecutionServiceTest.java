package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.config.OperationSandboxConfig;
import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        event = sys.receive("gsmarc://test/PaymentFailed/v1")
        sys.effect("gsmarc://test/OrderUpdate/v1", {"orderId": event["orderId"], "status": "failed"})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-001",
            rule,
            Map.of("orderId", "ORD-123", "reason", "insufficient_funds"),
            this::handleEffect,
            Set.of(archetypeId("PaymentFailed")),
            Set.of(archetypeId("OrderUpdate")));

    assertThat(result.success()).isTrue();
    assertThat(result.effects()).hasSize(1);
    assertThat(result.effects().getFirst().archetype()).isEqualTo(archetypeId("OrderUpdate"));
    assertThat(result.effects().getFirst().data()).containsEntry("orderId", "ORD-123");
    assertThat(result.effects().getFirst().data()).containsEntry("status", "failed");
  }

  @Test
  void executesClosedLoopRule() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        result = sys.effect("gsmarc://test/ValidatePayment/v1", {"orderId": event["orderId"]}).receive("gsmarc://test/ValidationResult/v1")
        if result["approved"]:
            sys.effect("gsmarc://test/OrderApproved/v1", {"orderId": event["orderId"]})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-002",
            rule,
            Map.of("orderId", "ORD-456"),
            this::handleEffect,
            Set.of(archetypeId("OrderCreated"), archetypeId("ValidationResult")),
            Set.of(archetypeId("ValidatePayment"), archetypeId("OrderApproved")));

    assertThat(result.success()).as("error: %s", result.error()).isTrue();
    assertThat(result.effects()).hasSize(2);
    assertThat(result.effects().get(0).closedLoop()).isTrue();
    assertThat(result.effects().get(1).archetype()).isEqualTo(archetypeId("OrderApproved"));
  }

  @Test
  void hostFunctionNowIsAvailable() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        ts = now()
        sys.effect("gsmarc://test/Timestamped/v1", {"ts": ts})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-003",
            rule,
            Map.of(),
            this::handleEffect,
            Set.of(archetypeId("Trigger")),
            Set.of(archetypeId("Timestamped")));

    assertThat(result.success()).isTrue();
    String ts = (String) result.effects().getFirst().data().get("ts");
    assertThat(ts).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
  }

  @Test
  void hostFunctionUuid7IsAvailable() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        id = uuid7()
        sys.effect("gsmarc://test/WithId/v1", {"id": id})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-004",
            rule,
            Map.of(),
            this::handleEffect,
            Set.of(archetypeId("Trigger")),
            Set.of(archetypeId("WithId")));

    assertThat(result.success()).isTrue();
    String id = (String) result.effects().getFirst().data().get("id");
    assertThat(id).matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  @Test
  void hostFunctionFullmatchIsAvailable() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        matched = fullmatch("[a-z]+", event["value"])
        sys.effect("gsmarc://test/Result/v1", {"matched": matched})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-005",
            rule,
            Map.of("value", "abc"),
            this::handleEffect,
            Set.of(archetypeId("Trigger")),
            Set.of(archetypeId("Result")));

    assertThat(result.success()).isTrue();
    assertThat(result.effects().getFirst().data().get("matched")).isEqualTo(true);
  }

  @Test
  void hostFunctionSearchIsAvailable() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        ver = search("v([0-9.]+)", event["tag"])
        sys.effect("gsmarc://test/Result/v1", {"version": ver})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-006",
            rule,
            Map.of("tag", "app-v2.3.1"),
            this::handleEffect,
            Set.of(archetypeId("Trigger")),
            Set.of(archetypeId("Result")));

    assertThat(result.success()).isTrue();
    assertThat(result.effects().getFirst().data().get("version")).isEqualTo("2.3.1");
  }

  @Test
  void syntaxErrorReturnsFailure() throws Exception {
    String rule = "event = sys.receive(\"gsmarc://test/T/v1\")\nif True\n  pass";

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-err",
            rule,
            Map.of(),
            this::handleEffect,
            Set.of(archetypeId("T")),
            Set.of());

    assertThat(result.success()).isFalse();
    assertThat(result.error()).isNotEmpty();
  }

  @Test
  void runtimeErrorReturnsFailure() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/T/v1")
        x = 1 / 0
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-err2",
            rule,
            Map.of(),
            this::handleEffect,
            Set.of(archetypeId("T")),
            Set.of());

    assertThat(result.success()).isFalse();
    assertThat(result.error()).containsIgnoringCase("division");
  }

  @Test
  void maxStepsEnforced() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/T/v1")
        x = 0
        for i in range(1000000):
            x = x + 1
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox, 100);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-budget",
            rule,
            Map.of(),
            this::handleEffect,
            Set.of(archetypeId("T")),
            Set.of());

    assertThat(result.success()).isFalse();
  }

  @Test
  void effectWithFullChain() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        result = (sys.effect("gsmarc://test/FraudCheck/v1", {"orderId": event["oid"]})
            .by("gsmarc://test/FraudGateway/v1")
            .receive("gsmarc://test/FraudResult/v1")
            .on("gsmarc://test/FraudReceiver/v1"))
        if result["approved"]:
            sys.effect("gsmarc://test/Cleared/v1", {"oid": event["oid"]})
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        sandbox.execute(
            "mech-007",
            rule,
            Map.of("oid", "O-1"),
            this::handleEffect,
            Set.of(archetypeId("Trigger"), archetypeId("FraudResult")),
            Set.of(archetypeId("FraudCheck"), archetypeId("Cleared")),
            Set.of(archetypeId("FraudReceiver")),
            Set.of(archetypeId("FraudGateway")));

    assertThat(result.success()).isTrue();
    assertThat(result.effects()).hasSize(2);
    EffectDto fraud = result.effects().get(0);
    assertThat(fraud.effectorArchetype()).isEqualTo(archetypeId("FraudGateway"));
    assertThat(fraud.feedbackArchetype()).isEqualTo(archetypeId("FraudResult"));
    assertThat(fraud.feedbackReceptorArchetype()).isEqualTo(archetypeId("FraudReceiver"));
  }

  @Test
  void containsRuntimeExceptionFromHandler() throws Exception {
    String rule =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        result = sys.effect("gsmarc://test/Bad/v1", {"x": 1}).receive("gsmarc://test/Response/v1")
        val = result["key"]
        """;

    OperationExecutionService sandbox =
        new OperationExecutionService(OperationSandboxConfig::createSandbox);
    OperationExecutionService.ExecutionResult result =
        execute(
            sandbox,
            "mech-err3",
            rule,
            Map.of(),
            effect -> {
              throw new IllegalStateException("Validation failed");
            },
            Set.of(archetypeId("Trigger"), archetypeId("Response")),
            Set.of(archetypeId("Bad")));

    assertThat(result.success()).isFalse();
    assertThat(result.error()).contains("Validation failed");
  }

  private OperationExecutionService.ExecutionResult execute(
      OperationExecutionService sandbox,
      String mechanismId,
      String rule,
      Map<String, Object> input,
      java.util.function.Function<EffectDto, Object> effectHandler,
      Set<String> receptorDataArchetypes,
      Set<String> effectorDataArchetypes) {
    return sandbox.execute(
        mechanismId,
        rule,
        input,
        effectHandler,
        receptorDataArchetypes,
        effectorDataArchetypes,
        Set.of(),
        Set.of());
  }

  private static String archetypeId(String title) {
    return "gsmarc://test/" + title + "/v1";
  }
}
