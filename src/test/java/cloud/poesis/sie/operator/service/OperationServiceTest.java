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
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        sys.effect("gsmarc://test/OrderConfirmation/v1", {"orderId": event["orderId"]})
        """;

    OperationFrameDto frame =
        frameWithTopology(
            mechanismAscId,
            ruleSource,
            List.of(archetypeId("OrderCreated")),
            Map.of(archetypeId("OrderConfirmation"), "gsmarc://gsm/Effector/v1"));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-001"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype())
        .isEqualTo(archetypeId("OrderConfirmation"));
    assertThat(response.effects().getFirst().data()).containsEntry("orderId", "ORD-001");
  }

  @Test
  void executesWithTopologyValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/AppraisalTrigger/v1")
        if len(event["relatedAscriptions"]) == 0:
            sys.effect("gsmarc://test/AppraisalFinding/v1", {
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
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("AppraisalTrigger"));
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Effector/v1",
            mechanismAscId,
            archetypeId("AppraisalFinding"));

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
    assertThat(response.effects().getFirst().archetype())
        .isEqualTo(archetypeId("AppraisalFinding"));
    assertThat(response.effects().getFirst().data()).containsEntry("findingType", "GAP");
  }

  @Test
  void failsWhenRuleUsesUnknownReceptorArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/NonExistentArchetype/v1")
        sys.effect("gsmarc://test/SomeOutput/v1", {"message": "test"})
        """;

    // Permissive schema so input validation passes — the sandbox archetype check is what we test
    JsonNode permissiveSchema =
        MAPPER.createObjectNode().put("$id", archetypeId("AppraisalTrigger")).put("type", "object");
    UUID receptorArchId = UUID.randomUUID();
    ArchetypeAscriptionDto receptorArchetype =
        new ArchetypeAscriptionDto(
            receptorArchId, "ACTIVE", 1, "AppraisalTrigger", permissiveSchema);
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("AppraisalTrigger"));

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
    assertThat(response.error()).contains("Unknown receptor data archetype");
  }

  @Test
  void failsClosedWhenFrameHasNoReceptorArchetypes() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"gsmarc://test/UnexpectedTrigger/v1\")";
    OperationFrameDto frame = frameWithNoSchemas(mechanismAscId, ruleSource);
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown receptor data archetype");
  }

  @Test
  void failsClosedWhenTriggerSchemaIsUnresolved() {
    UUID mechanismAscId = UUID.randomUUID();
    String triggerId = archetypeId("Trigger");
    ReceptorAscriptionDto trigger = receptor(mechanismAscId, triggerId);
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, "sys.receive(\"" + triggerId + "\")"),
            List.of(trigger),
            List.of(),
            Map.of());
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unresolved receptor schema").contains(triggerId);
  }

  @Test
  void failsClosedWhenEffectSchemaIsUnresolved() {
    UUID mechanismAscId = UUID.randomUUID();
    String triggerId = archetypeId("Trigger");
    String effectId = archetypeId("Effect");
    ArchetypeAscriptionDto triggerSchema = permissiveArchetype(triggerId);
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(
                mechanismAscId,
                "event = sys.receive(\""
                    + triggerId
                    + "\")\nsys.effect(\""
                    + effectId
                    + "\", event)"),
            List.of(receptor(mechanismAscId, triggerId)),
            List.of(effector(mechanismAscId, effectId)),
            Map.of(triggerSchema.id(), triggerSchema));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unresolved effector schema").contains(effectId);
  }

  @Test
  void failsClosedWhenFeedbackSchemaIsUnresolved() {
    UUID mechanismAscId = UUID.randomUUID();
    String triggerId = archetypeId("Trigger");
    String feedbackId = archetypeId("Feedback");
    String effectId = archetypeId("Effect");
    ArchetypeAscriptionDto triggerSchema = permissiveArchetype(triggerId);
    ArchetypeAscriptionDto effectSchema = permissiveArchetype(effectId);
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(
                mechanismAscId,
                "event = sys.receive(\""
                    + triggerId
                    + "\")\nsys.effect(\""
                    + effectId
                    + "\", event).receive(\""
                    + feedbackId
                    + "\")"),
            List.of(receptor(mechanismAscId, triggerId), receptor(mechanismAscId, feedbackId)),
            List.of(effector(mechanismAscId, effectId)),
            Map.of(triggerSchema.id(), triggerSchema, effectSchema.id(), effectSchema));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unresolved receptor schema").contains(feedbackId);
  }

  @Test
  void failsWhenRuleUsesUnknownEffectorArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/AppraisalTrigger/v1")
        sys.effect("gsmarc://test/NonExistentOutput/v1", {"message": "test"})
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
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("AppraisalTrigger"));
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Effector/v1",
            mechanismAscId,
            archetypeId("AppraisalFinding"));

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
  void failsWhenRuleUsesUnknownReceptorPortArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        "event = sys.receive(\"gsmarc://test/Trigger/v1\")"
            + ".on(\"gsmarc://test/UnknownReceptorPort/v1\")";
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("ActualReceptorPort"),
            mechanismAscId,
            archetypeId("Trigger"));
    ArchetypeAscriptionDto triggerSchema = permissiveArchetype(archetypeId("Trigger"));
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(),
            Map.of(triggerSchema.id(), triggerSchema));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown receptor port archetype");
  }

  @Test
  void failsWhenRuleUsesUnknownEffectorPortArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        sys.effect("gsmarc://test/Output/v1").by("gsmarc://test/UnknownEffectorPort/v1")
        """;
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("ActualReceptorPort"),
            mechanismAscId,
            archetypeId("Trigger"));
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("ActualEffectorPort"),
            mechanismAscId,
            archetypeId("Output"));
    ArchetypeAscriptionDto triggerSchema = permissiveArchetype(archetypeId("Trigger"));
    ArchetypeAscriptionDto outputSchema = permissiveArchetype(archetypeId("Output"));
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(effector),
            Map.of(triggerSchema.id(), triggerSchema, outputSchema.id(), outputSchema));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown effector port archetype");
  }

  @Test
  void failsWhenRuleUsesUnknownFeedbackDataArchetype() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/Trigger/v1")
        sys.effect("gsmarc://test/Output/v1").receive("gsmarc://test/UnknownFeedback/v1")
        """;
    ReceptorAscriptionDto trigger =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("TriggerPort"),
            mechanismAscId,
            archetypeId("Trigger"));
    ReceptorAscriptionDto feedback =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("FeedbackPort"),
            mechanismAscId,
            archetypeId("Feedback"));
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            archetypeId("OutputPort"),
            mechanismAscId,
            archetypeId("Output"));
    ArchetypeAscriptionDto triggerSchema = permissiveArchetype(archetypeId("Trigger"));
    ArchetypeAscriptionDto feedbackSchema = permissiveArchetype(archetypeId("Feedback"));
    ArchetypeAscriptionDto outputSchema = permissiveArchetype(archetypeId("Output"));
    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(trigger, feedback),
            List.of(effector),
            Map.of(
                triggerSchema.id(),
                triggerSchema,
                feedbackSchema.id(),
                feedbackSchema,
                outputSchema.id(),
                outputSchema));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(mechanismAscId, Map.of()));

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Unknown feedback data archetype");
  }

  @Test
  void rejectsOperationInputFailingSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"gsmarc://test/AppraisalTrigger/v1\")";

    JsonNode triggerSchema = triggerArchetypeSchema();
    UUID triggerArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);

    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("AppraisalTrigger"));

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
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        result = sys.effect("gsmarc://test/ValidatePayment/v1", {"amount": event["amount"]}).receive("gsmarc://test/PaymentResult/v1")
        if result["valid"]:
            sys.effect("gsmarc://test/OrderApproved/v1", {"orderId": event["orderId"]})
        """;

    OperationFrameDto frame =
        frameWithTopology(
            mechanismAscId,
            ruleSource,
            List.of(archetypeId("OrderCreated"), archetypeId("PaymentResult")),
            Map.of(
                archetypeId("ValidatePayment"),
                "gsmarc://gsm/Effector/v1",
                archetypeId("OrderApproved"),
                "gsmarc://gsm/Effector/v1"));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    MechanismEffectorExecutionService feedbackDispatcher =
        new MechanismEffectorExecutionService() {
          @Override
          public boolean supports(EffectDto effect) {
            return true;
          }

          @Override
          public Map<String, Object> dispatch(EffectDto effect) {
            return Map.of("valid", true);
          }
        };
    OperationService service =
        new OperationService(frameResolver, inputValidator, sandbox, List.of(feedbackDispatcher));

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-002", "amount", 100));

    OperationResponseDto response = service.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(2);
  }

  @Test
  void returnsFailureWhenRuleHasSyntaxError() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"gsmarc://test/T/v1\")\nif True\n  pass";

    OperationFrameDto frame =
        frameWithTopology(
            mechanismAscId,
            ruleSource,
            List.of(archetypeId("OrderCreated"), archetypeId("RelaySignal")),
            Map.of(
                archetypeId("RelaySignal"),
                archetypeId("RelayEffector"),
                archetypeId("OrderConfirmation"),
                "gsmarc://gsm/Effector/v1"));
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
        event = sys.receive("gsmarc://test/Trigger/v1")
        result = sys.effect("gsmarc://test/Request/v1", {"id": "123"}).receive("gsmarc://test/Response/v1")
        val = result["value"]
        """;

    JsonNode responseSchema = responseArchetypeSchema();

    ArchetypeAscriptionDto responseArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "Response", responseSchema);
    ArchetypeAscriptionDto triggerArchetype = permissiveArchetype(archetypeId("Trigger"));
    ArchetypeAscriptionDto requestArchetype = permissiveArchetype(archetypeId("Request"));
    ReceptorAscriptionDto triggerReceptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("Trigger"));
    ReceptorAscriptionDto responseReceptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("Response"));
    EffectorAscriptionDto requestEffector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Effector/v1",
            mechanismAscId,
            archetypeId("Request"));

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(triggerReceptor, responseReceptor),
            List.of(requestEffector),
            Map.of(
                responseArchetype.id(),
                responseArchetype,
                triggerArchetype.id(),
                triggerArchetype,
                requestArchetype.id(),
                requestArchetype));
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
        event = sys.receive("gsmarc://test/Trigger/v1")
        sys.effect("gsmarc://test/StrictOutput/v1", {"wrongField": "value"})
        """;

    ObjectNode outputSchema = MAPPER.createObjectNode();
    outputSchema.put("$id", archetypeId("StrictOutput"));
    outputSchema.put("type", "object");
    outputSchema.set("required", MAPPER.createArrayNode().add("requiredField"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("requiredField", MAPPER.createObjectNode().put("type", "string"));
    outputSchema.set("properties", props);

    UUID outputArchId = UUID.randomUUID();
    ArchetypeAscriptionDto outputArchetype =
        new ArchetypeAscriptionDto(outputArchId, "ACTIVE", 1, "StrictOutput", outputSchema);
    ArchetypeAscriptionDto triggerArchetype = permissiveArchetype(archetypeId("Trigger"));
    ReceptorAscriptionDto triggerReceptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("Trigger"));

    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Effector/v1",
            mechanismAscId,
            archetypeId("StrictOutput"));

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(triggerReceptor),
            List.of(effector),
            Map.of(outputArchId, outputArchetype, triggerArchetype.id(), triggerArchetype));
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
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        signal = sys.effect("gsmarc://test/RelaySignal/v1", {"orderId": event["orderId"]}).by("gsmarc://test/RelayEffector/v1").receive("gsmarc://test/RelaySignal/v1")
        sys.effect("gsmarc://test/OrderConfirmation/v1", {"orderId": signal["orderId"], "relayed": True})
        """;

    OperationFrameDto frame =
        frameWithTopology(
            mechanismAscId,
            ruleSource,
            List.of(archetypeId("OrderCreated"), archetypeId("RelaySignal")),
            Map.of(
                archetypeId("RelaySignal"),
                archetypeId("RelayEffector"),
                archetypeId("OrderConfirmation"),
                archetypeId("RelayEffector")));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-RELAY"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(2);
    assertThat(response.effects().get(0).archetype()).isEqualTo(archetypeId("RelaySignal"));
    assertThat(response.effects().get(0).effectorArchetype())
        .isEqualTo(archetypeId("RelayEffector"));
    assertThat(response.effects().get(0).closedLoop()).isTrue();
    assertThat(response.effects().get(1).archetype()).isEqualTo(archetypeId("OrderConfirmation"));
    assertThat(response.effects().get(1).data()).containsEntry("orderId", "ORD-RELAY");
    assertThat(response.effects().get(1).data()).containsEntry("relayed", true);
  }

  @Test
  void executesRelayFireAndForgetEffect() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        sys.effect("gsmarc://test/RelaySignal/v1", {"orderId": event["orderId"]}).by("gsmarc://test/RelayEffector/v1")
        """;

    OperationFrameDto frame =
        frameWithTopology(
            mechanismAscId,
            ruleSource,
            List.of(archetypeId("OrderCreated")),
            Map.of(archetypeId("RelaySignal"), archetypeId("RelayEffector")));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-FNF"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo(archetypeId("RelaySignal"));
    assertThat(response.effects().getFirst().effectorArchetype())
        .isEqualTo(archetypeId("RelayEffector"));
    assertThat(response.effects().getFirst().closedLoop()).isFalse();
    assertThat(response.effects().getFirst().data()).containsEntry("orderId", "ORD-FNF");
  }

  @Test
  void passesValidationWhenInputMatchesOneOfMultipleReceptors() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("gsmarc://test/OrderCreated/v1")
        sys.effect("gsmarc://test/OrderConfirmation/v1", {"orderId": event["orderId"]})
        """;

    // Trigger receptor — matches the input
    JsonNode triggerSchema =
        MAPPER
            .createObjectNode()
            .put("$id", archetypeId("OrderCreated"))
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
            .put("$id", archetypeId("Response"))
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
        new ArchetypeAscriptionDto(feedbackArchId, "ACTIVE", 1, "Response", feedbackSchema);

    ReceptorAscriptionDto triggerReceptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("OrderCreated"));
    ReceptorAscriptionDto feedbackReceptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Receptor/v1",
            mechanismAscId,
            archetypeId("Response"));
    ArchetypeAscriptionDto confirmationArchetype =
        permissiveArchetype(archetypeId("OrderConfirmation"));
    EffectorAscriptionDto confirmationEffector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://gsm/Effector/v1",
            mechanismAscId,
            archetypeId("OrderConfirmation"));

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(feedbackReceptor, triggerReceptor),
            List.of(confirmationEffector),
            Map.of(
                triggerArchId,
                triggerArchetype,
                feedbackArchId,
                feedbackArchetype,
                confirmationArchetype.id(),
                confirmationArchetype));
    when(frameResolver.resolve(mechanismAscId)).thenReturn(frame);

    // Input matches trigger receptor but NOT feedback receptor
    Map<String, Object> operationInput = Map.of("orderId", "ORD-MULTI");

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, operationInput);

    OperationResponseDto response = operationService.operate(request);

    // Must succeed — input matches at least one receptor (trigger)
    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype())
        .isEqualTo(archetypeId("OrderConfirmation"));
  }

  // --- Frame helpers ---

  private OperationFrameDto frameWithNoSchemas(UUID ascId, String ruleSource) {
    return new OperationFrameDto(
        mechanismAscription(ascId, ruleSource), List.of(), List.of(), Collections.emptyMap());
  }

  private OperationFrameDto frameWithTopology(
      UUID mechanismId,
      String ruleSource,
      List<String> receptorDataArchetypes,
      Map<String, String> effectorPortByDataArchetype) {
    Map<UUID, ArchetypeAscriptionDto> archetypes = new java.util.HashMap<>();
    List<ReceptorAscriptionDto> receptors =
        receptorDataArchetypes.stream()
            .map(
                archetypeUri -> {
                  ArchetypeAscriptionDto archetype = permissiveArchetype(archetypeUri);
                  archetypes.put(archetype.id(), archetype);
                  return new ReceptorAscriptionDto(
                      UUID.randomUUID(),
                      "ACTIVE",
                      1,
                      "gsmarc://gsm/Receptor/v1",
                      mechanismId,
                      archetypeUri);
                })
            .toList();
    List<EffectorAscriptionDto> effectors =
        effectorPortByDataArchetype.entrySet().stream()
            .map(
                entry -> {
                  ArchetypeAscriptionDto archetype = permissiveArchetype(entry.getKey());
                  archetypes.put(archetype.id(), archetype);
                  return new EffectorAscriptionDto(
                      UUID.randomUUID(),
                      "ACTIVE",
                      1,
                      entry.getValue(),
                      mechanismId,
                      entry.getKey());
                })
            .toList();
    return new OperationFrameDto(
        mechanismAscription(mechanismId, ruleSource), receptors, effectors, archetypes);
  }

  private ArchetypeAscriptionDto permissiveArchetype(String archetypeUri) {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("$id", archetypeUri);
    schema.put("type", "object");
    schema.put("additionalProperties", true);
    return new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, archetypeUri, schema);
  }

  private ReceptorAscriptionDto receptor(UUID mechanismId, String dataArchetypeId) {
    return new ReceptorAscriptionDto(
        UUID.randomUUID(), "ACTIVE", 1, "gsmarc://gsm/Receptor/v1", mechanismId, dataArchetypeId);
  }

  private EffectorAscriptionDto effector(UUID mechanismId, String dataArchetypeId) {
    return new EffectorAscriptionDto(
        UUID.randomUUID(), "ACTIVE", 1, "gsmarc://gsm/Effector/v1", mechanismId, dataArchetypeId);
  }

  private MechanismAscriptionDto mechanismAscription(UUID ascId, String ruleSource) {
    return new MechanismAscriptionDto(ascId, "ACTIVE", 1, UUID.randomUUID(), "test", ruleSource);
  }

  private JsonNode triggerArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("$id", archetypeId("AppraisalTrigger"));
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
    schema.put("$id", archetypeId("AppraisalFinding"));
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
    schema.put("$id", archetypeId("Response"));
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("value"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("value", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);
    return schema;
  }

  private static String archetypeId(String title) {
    return "gsmarc://test/" + title + "/v1";
  }
}
