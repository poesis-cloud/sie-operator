package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.config.OperationSandboxConfig;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.OperationFrameResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private OperationFrameResolutionService frameResolver;
  @Mock private DefinitionManagerClient definitionManagerClient;

  private OperationInputValidationService inputValidator;
  private OperationExecutionService sandbox;
  private OperationService operationService;

  @BeforeEach
  void setUp() {
    inputValidator = new OperationInputValidationService();
    sandbox = new OperationExecutionService(OperationSandboxConfig::createSandbox);
    List<MechanismEffectorExecutionService> dispatchers =
        List.of(
            new MechanismRelayEffectorExecutionService(definitionManagerClient, null),
            new MechanismEffectorExecutionService() {
              @Override
              public boolean supports(EffectDto effect) {
                return true;
              }

              @Override
              public Map<String, Object> dispatch(EffectDto effect) {
                return null;
              }
            });
    operationService = new OperationService(frameResolver, inputValidator, sandbox, dispatchers);
  }

  @Test
  void executesFireAndForgetMechanism() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        sys.effect("OrderConfirmation", {"orderId": event["orderId"]})
        """;

    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-001"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("OrderConfirmation");
    assertThat(response.effects().getFirst().data()).containsEntry("orderId", "ORD-001");
  }

  @Test
  void executesWithTopologyValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("AppraisalTrigger")
        if len(event["relatedAscriptions"]) == 0:
            sys.effect("AppraisalFinding", {
                "ruleType": event["ruleType"],
                "findingType": "GAP",
                "subjectType": event["subjectType"],
                "subjectDefinitionId": event["subjectDefinitionId"],
                "severity": "HIGH",
                "message": "No norms found"
            })
        """;

    JsonNode triggerSchema = triggerArchetypeSchema();
    JsonNode findingSchema = findingArchetypeSchema();

    UUID triggerArchId = UUID.randomUUID();
    UUID findingArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);
    ArchetypeAscriptionDto findingArchetype =
        new ArchetypeAscriptionDto(findingArchId, "ACTIVE", 1, "AppraisalFinding", findingSchema);

    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, triggerArchId);
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, findingArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(effector),
            Map.of(triggerArchId, triggerArchetype, findingArchId, findingArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    UUID subjectDefId = UUID.randomUUID();
    Map<String, Object> operationInput =
        Map.of(
            "ruleType", "gsm:rules/appraisal/directive/norm/operationalization",
            "subjectType", "DIRECTIVE",
            "subjectDefinitionId", subjectDefId.toString(),
            "subject", Map.of("modal", "MUST", "verb", "protect", "purpose", "sec"),
            "relatedAscriptions", List.of());

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, operationInput);

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("AppraisalFinding");
    assertThat(response.effects().getFirst().data()).containsEntry("findingType", "GAP");
  }

  @Test
  void failsWhenRuleUsesUnknownReceptorArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("NonExistentArchetype")
        sys.effect("SomeOutput", {"message": "test"})
        """;

    // Permissive schema so input validation passes — the sandbox archetype check is what we test
    JsonNode permissiveSchema = MAPPER.createObjectNode().put("type", "object");
    UUID receptorArchId = UUID.randomUUID();
    ArchetypeAscriptionDto receptorArchetype =
        new ArchetypeAscriptionDto(
            receptorArchId, "ACTIVE", 1, "AppraisalTrigger", permissiveSchema);
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, receptorArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(),
            Map.of(receptorArchId, receptorArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown receptor archetype");
  }

  @Test
  void failsWhenRuleUsesUnknownEffectorArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("AppraisalTrigger")
        sys.effect("NonExistentOutput", {"message": "test"})
        """;

    JsonNode triggerSchema = triggerArchetypeSchema();
    UUID triggerArchId = UUID.randomUUID();
    UUID findingArchId = UUID.randomUUID();
    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);
    ArchetypeAscriptionDto findingArchetype =
        new ArchetypeAscriptionDto(
            findingArchId, "ACTIVE", 1, "AppraisalFinding", findingArchetypeSchema());
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, triggerArchId);
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, findingArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(effector),
            Map.of(triggerArchId, triggerArchetype, findingArchId, findingArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    Map<String, Object> operationInput =
        Map.of(
            "ruleType",
            "test",
            "subjectType",
            "DIRECTIVE",
            "subjectDefinitionId",
            UUID.randomUUID().toString(),
            "subject",
            Map.of("key", "val"));
    OperationRequestDto request = new OperationRequestDto(mechanismAscId, operationInput);

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown effector data archetype");
  }

  @Test
  void rejectsOperationInputFailingSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"AppraisalTrigger\")";

    JsonNode triggerSchema = triggerArchetypeSchema();
    UUID triggerArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);

    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, triggerArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(),
            Map.of(triggerArchId, triggerArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    // Missing required fields — should fail validation
    Map<String, Object> badInput = Map.of("unknownField", "value");

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, badInput);

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("validation failed");
  }

  @Test
  void executesClosedLoopWithHandler() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        result = sys.effect("ValidatePayment", {"amount": event["amount"]}).receive("PaymentResult")
        if result["valid"]:
            sys.effect("OrderApproved", {"orderId": event["orderId"]})
        """;

    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-002", "amount", 100));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response).isNotNull();
  }

  @Test
  void returnsFailureWhenRuleHasSyntaxError() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"T\")\nif True\n  pass";

    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).isNotEmpty();
  }

  @Test
  void returnsFailureWhenMechanismNotFound() {
    UUID mechanismAscId = UUID.randomUUID();
    when(frameResolver.resolve(mechanismAscId))
        .thenThrow(
            new OperationFrameResolutionException(
                "Mechanism ascription not found: " + mechanismAscId));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("not found");
  }

  @Test
  void returnsFailureWhenMechanismHasNoRuleSource() {
    UUID mechanismAscId = UUID.randomUUID();
    when(frameResolver.resolve(mechanismAscId))
        .thenThrow(
            new OperationFrameResolutionException("Mechanism ascription has no rule: test-id"));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("rule");
  }

  @Test
  void failsWhenClosedLoopResponseFailsSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("Trigger")
        result = sys.effect("Request", {"id": "123"}).receive("Response")
        val = result["value"]
        """;

    JsonNode responseSchema = responseArchetypeSchema();

    ArchetypeAscriptionDto responseArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "Response", responseSchema);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(),
            List.of(),
            Map.of(responseArchetype.id(), responseArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    // Dispatcher returns a map missing the required "value" field
    MechanismEffectorExecutionService invalidResponseDispatcher =
        new MechanismEffectorExecutionService() {
          @Override
          public boolean supports(EffectDto effect) {
            return true;
          }

          @Override
          public Map<String, Object> dispatch(EffectDto effect) {
            return Map.of("wrongField", 42);
          }
        };
    OperationService service =
        new OperationService(
            frameResolver, inputValidator, sandbox, List.of(invalidResponseDispatcher));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = service.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Closed-loop response validation failed");
  }

  @Test
  void failsWhenEffectDataFailsSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("Trigger")
        sys.effect("StrictOutput", {"wrongField": "value"})
        """;

    ObjectNode outputSchema = MAPPER.createObjectNode();
    outputSchema.put("type", "object");
    outputSchema.set("required", MAPPER.createArrayNode().add("requiredField"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("requiredField", MAPPER.createObjectNode().put("type", "string"));
    outputSchema.set("properties", props);

    UUID outputArchId = UUID.randomUUID();
    ArchetypeAscriptionDto outputArchetype =
        new ArchetypeAscriptionDto(outputArchId, "ACTIVE", 1, "StrictOutput", outputSchema);

    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, outputArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(),
            List.of(effector),
            Map.of(outputArchId, outputArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Effect data validation failed");
  }

  @Test
  void executesRelayClosedLoopEffect() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        signal = sys.effect("RelaySignal", {"orderId": event["orderId"]}).by("RelayEffector").receive("RelaySignal")
        sys.effect("OrderConfirmation", {"orderId": signal["orderId"], "relayed": True})
        """;

    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-RELAY"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(2);
    assertThat(response.effects().get(0).archetype()).isEqualTo("RelaySignal");
    assertThat(response.effects().get(0).effectorArchetype()).isEqualTo("RelayEffector");
    assertThat(response.effects().get(0).closedLoop()).isTrue();
    assertThat(response.effects().get(1).archetype()).isEqualTo("OrderConfirmation");
    assertThat(response.effects().get(1).data()).containsEntry("orderId", "ORD-RELAY");
    assertThat(response.effects().get(1).data()).containsEntry("relayed", true);
  }

  @Test
  void executesRelayFireAndForgetEffect() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        sys.effect("RelaySignal", {"orderId": event["orderId"]}).by("RelayEffector")
        """;

    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-FNF"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("RelaySignal");
    assertThat(response.effects().getFirst().effectorArchetype()).isEqualTo("RelayEffector");
    assertThat(response.effects().getFirst().closedLoop()).isFalse();
    assertThat(response.effects().getFirst().data()).containsEntry("orderId", "ORD-FNF");
  }

  @Test
  void passesValidationWhenInputMatchesOneOfMultipleReceptors() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        sys.effect("OrderConfirmation", {"orderId": event["orderId"]})
        """;

    // Trigger receptor — matches the input
    JsonNode triggerSchema =
        MAPPER
            .createObjectNode()
            .put("type", "object")
            .set("required", MAPPER.createArrayNode().add("orderId"));
    ((ObjectNode) triggerSchema)
        .set(
            "properties",
            MAPPER
                .createObjectNode()
                .set("orderId", MAPPER.createObjectNode().put("type", "string")));

    // Feedback receptor — does NOT match the input (requires different fields)
    JsonNode feedbackSchema =
        MAPPER
            .createObjectNode()
            .put("type", "object")
            .set("required", MAPPER.createArrayNode().add("statusCode"));
    ((ObjectNode) feedbackSchema)
        .set(
            "properties",
            MAPPER
                .createObjectNode()
                .set("statusCode", MAPPER.createObjectNode().put("type", "integer")));

    UUID triggerArchId = UUID.randomUUID();
    UUID feedbackArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "OrderCreated", triggerSchema);
    ArchetypeAscriptionDto feedbackArchetype =
        new ArchetypeAscriptionDto(feedbackArchId, "ACTIVE", 1, "HttpResponse", feedbackSchema);

    ReceptorAscriptionDto triggerReceptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, triggerArchId);
    ReceptorAscriptionDto feedbackReceptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechanismAscId, feedbackArchId);

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(feedbackReceptor, triggerReceptor),
            List.of(),
            Map.of(triggerArchId, triggerArchetype, feedbackArchId, feedbackArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    // Input matches trigger receptor but NOT feedback receptor
    Map<String, Object> operationInput = Map.of("orderId", "ORD-MULTI");

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, operationInput);

    OperationResponseDto response = operationService.operate(request);

    // Must succeed — input matches at least one receptor (trigger)
    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("OrderConfirmation");
  }

  // --- Frame helpers ---

  private OperationFrameDto frameWithNoSchemas(UUID ascId, String ruleSource) {
    return new OperationFrameDto(
        mechanismAscription(ascId, ruleSource), List.of(), List.of(), Collections.emptyMap());
  }

  private MechanismAscriptionDto mechanismAscription(UUID ascId, String ruleSource) {
    return new MechanismAscriptionDto(ascId, "ACTIVE", 1, UUID.randomUUID(), "test", ruleSource);
  }

  private JsonNode triggerArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set(
        "required",
        MAPPER
            .createArrayNode()
            .add("ruleType")
            .add("subjectType")
            .add("subjectDefinitionId")
            .add("subject"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectDefinitionId", MAPPER.createObjectNode().put("type", "string"));
    props.set("subject", MAPPER.createObjectNode().put("type", "object"));
    props.set("relatedAscriptions", MAPPER.createObjectNode().put("type", "array"));
    schema.set("properties", props);
    return schema;
  }

  private JsonNode findingArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set(
        "required",
        MAPPER
            .createArrayNode()
            .add("ruleType")
            .add("findingType")
            .add("subjectType")
            .add("subjectDefinitionId")
            .add("severity")
            .add("message"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("findingType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectDefinitionId", MAPPER.createObjectNode().put("type", "string"));
    props.set("severity", MAPPER.createObjectNode().put("type", "string"));
    props.set("message", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);
    return schema;
  }

  private JsonNode responseArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("value"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("value", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);
    return schema;
  }
}
