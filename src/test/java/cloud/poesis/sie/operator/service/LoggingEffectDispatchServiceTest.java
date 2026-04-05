package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingEffectDispatchServiceTest {

  private final LoggingEffectDispatchService dispatcher = new LoggingEffectDispatchService();

  @Test
  void supportsAnyEffect() {
    EffectDto effect = EffectDto.fireAndForget("SomeArchetype", Map.of());
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void dispatchReturnsNull() {
    EffectDto effect = EffectDto.fireAndForget("SomeArchetype", Map.of("key", "value"));
    Map<String, Object> result = dispatcher.dispatch(effect);
    assertThat(result).isNull();
  }

  @Test
  void supportsClosedLoopEffect() {
    EffectDto effect = new EffectDto("Arch", Map.of(), "Effector", "Feedback", "Receptor", true);
    assertThat(dispatcher.supports(effect)).isTrue();
  }
}
