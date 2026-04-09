package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MechanismRelayEffectorExecutionServiceTest {

  private static final ObjectNode EMPTY_SCHEMA = new ObjectMapper().createObjectNode();

  @Mock private DefinitionManagerClient client;
  @Mock private OperationService operationService;

  private MechanismRelayEffectorExecutionService dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new MechanismRelayEffectorExecutionService(client, operationService);
  }

  @Test
  void supportsEffectsWithRelayEffectorArchetype() {
    EffectDto effect =
        new EffectDto("RelaySignal", Map.of("body", "test"), "RelayEffector", null, null, false);
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void supportsEffectsWithAnyNonNullEffectorArchetype() {
    EffectDto effect =
        new EffectDto("CustomSignal", Map.of("data", "value"), "CustomEffector", null, null, false);
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void doesNotSupportEffectsWithNullEffectorArchetype() {
    EffectDto effect = EffectDto.fireAndForget("RelaySignal", Map.of("body", "test"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void doesNotSupportEffectsWithEmptyEffectorArchetype() {
    EffectDto effect = new EffectDto("RelaySignal", Map.of("body", "test"), "", null, null, false);
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void dispatchReturnsCausalSignalData() {
    Map<String, Object> signalData = Map.of("body", Map.of("orderId", "ORD-123", "amount", 42));
    EffectDto effect = new EffectDto("RelaySignal", signalData, "RelayEffector", null, null, false);

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).isEqualTo(signalData);
  }

  @Test
  void dispatchReturnsEmptyDataWhenSignalHasNoBody() {
    EffectDto effect = new EffectDto("RelaySignal", Map.of(), "RelayEffector", null, null, false);

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).isEmpty();
  }

  @Test
  void dispatchClosedLoopReturnsDataForFeedback() {
    Map<String, Object> signalData = Map.of("body", Map.of("status", "propagated"));
    EffectDto effect =
        new EffectDto(
            "RelaySignal", signalData, "RelayEffector", "RelaySignal", "RelayReceptor", true);

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).isEqualTo(signalData);
    assertThat(result).containsKey("body");
  }

  @Test
  void chainResolvesDownstreamMechanismViaInteraction() {
    UUID mechanismId = UUID.randomUUID();
    UUID effectorId = UUID.randomUUID();
    UUID effectorArchetypeId = UUID.randomUUID();
    UUID interactionId = UUID.randomUUID();
    UUID receptorId = UUID.randomUUID();
    UUID downstreamMechanismId = UUID.randomUUID();
    UUID receptorArchetypeId = UUID.randomUUID();

    Map<String, Object> inputData = Map.of("body", Map.of("key", "value"));
    Map<String, Object> outputData = Map.of("body", Map.of("result", "ok"));

    EffectDto effect = new EffectDto("RelaySignal", inputData, "RelayEffector", null, null, false);

    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechanismId, effectorArchetypeId);
    ArchetypeAscriptionDto effectorArchetype =
        new ArchetypeAscriptionDto(effectorArchetypeId, "ACTIVE", 1, "RelayEffector", EMPTY_SCHEMA);

    OperationFrameDto frame =
        new OperationFrameDto(
            null, List.of(), List.of(effector), Map.of(effectorArchetypeId, effectorArchetype));

    InteractionAscriptionDto interaction =
        new InteractionAscriptionDto(interactionId, "ACTIVE", 1, effectorId, receptorId);
    ReceptorAscriptionDto downstream =
        new ReceptorAscriptionDto(
            receptorId, "ACTIVE", 1, downstreamMechanismId, receptorArchetypeId);

    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(interaction));
    when(client.getReceptorAscription(receptorId)).thenReturn(downstream);

    EffectDto downstreamEffect = EffectDto.fireAndForget("Output", outputData);
    when(operationService.operate(new OperationRequestDto(downstreamMechanismId, inputData)))
        .thenReturn(OperationResponseDto.success(List.of(downstreamEffect)));

    Map<String, Object> result = dispatcher.dispatch(effect, frame);

    assertThat(result).isEqualTo(outputData);
    verify(operationService).operate(new OperationRequestDto(downstreamMechanismId, inputData));
  }

  @Test
  void chainFallsBackToPassthroughWhenNoInteractions() {
    UUID mechanismId = UUID.randomUUID();
    UUID effectorId = UUID.randomUUID();
    UUID effectorArchetypeId = UUID.randomUUID();

    Map<String, Object> inputData = Map.of("body", Map.of("key", "value"));

    EffectDto effect = new EffectDto("RelaySignal", inputData, "RelayEffector", null, null, false);

    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechanismId, effectorArchetypeId);
    ArchetypeAscriptionDto effectorArchetype =
        new ArchetypeAscriptionDto(effectorArchetypeId, "ACTIVE", 1, "RelayEffector", EMPTY_SCHEMA);

    OperationFrameDto frame =
        new OperationFrameDto(
            null, List.of(), List.of(effector), Map.of(effectorArchetypeId, effectorArchetype));

    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of());

    Map<String, Object> result = dispatcher.dispatch(effect, frame);

    assertThat(result).isEqualTo(inputData);
    verifyNoInteractions(operationService);
  }

  @Test
  void chainThrowsWhenDownstreamMechanismFails() {
    UUID mechanismId = UUID.randomUUID();
    UUID effectorId = UUID.randomUUID();
    UUID effectorArchetypeId = UUID.randomUUID();
    UUID interactionId = UUID.randomUUID();
    UUID receptorId = UUID.randomUUID();
    UUID downstreamMechanismId = UUID.randomUUID();
    UUID receptorArchetypeId = UUID.randomUUID();

    Map<String, Object> inputData = Map.of("body", "test");

    EffectDto effect = new EffectDto("RelaySignal", inputData, "RelayEffector", null, null, false);

    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechanismId, effectorArchetypeId);
    ArchetypeAscriptionDto effectorArchetype =
        new ArchetypeAscriptionDto(effectorArchetypeId, "ACTIVE", 1, "RelayEffector", EMPTY_SCHEMA);

    OperationFrameDto frame =
        new OperationFrameDto(
            null, List.of(), List.of(effector), Map.of(effectorArchetypeId, effectorArchetype));

    InteractionAscriptionDto interaction =
        new InteractionAscriptionDto(interactionId, "ACTIVE", 1, effectorId, receptorId);
    ReceptorAscriptionDto downstream =
        new ReceptorAscriptionDto(
            receptorId, "ACTIVE", 1, downstreamMechanismId, receptorArchetypeId);

    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(interaction));
    when(client.getReceptorAscription(receptorId)).thenReturn(downstream);
    when(operationService.operate(new OperationRequestDto(downstreamMechanismId, inputData)))
        .thenReturn(OperationResponseDto.failure("rule error"));

    assertThatThrownBy(() -> dispatcher.dispatch(effect, frame))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("failed");
  }

  @Test
  void chainFallsBackToPassthroughWhenNullFrame() {
    Map<String, Object> inputData = Map.of("body", "test");
    EffectDto effect = new EffectDto("RelaySignal", inputData, "RelayEffector", null, null, false);

    Map<String, Object> result = dispatcher.dispatch(effect, null);

    assertThat(result).isEqualTo(inputData);
    verifyNoInteractions(client);
    verifyNoInteractions(operationService);
  }
}
