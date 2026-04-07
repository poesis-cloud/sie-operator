package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MechanismRelayEffectorExecutionServiceTest {

  private MechanismRelayEffectorExecutionService dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new MechanismRelayEffectorExecutionService();
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
}
