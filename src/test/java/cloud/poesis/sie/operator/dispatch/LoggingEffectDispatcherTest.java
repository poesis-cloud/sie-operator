package cloud.poesis.sie.operator.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.starlark.EffectRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoggingEffectDispatcherTest {

  private final LoggingEffectDispatcher dispatcher = new LoggingEffectDispatcher();

  @Test
  void supportsAnyEffect() {
    EffectRecord effect = EffectRecord.fireAndForget("SomeArchetype", Map.of());
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void dispatchReturnsNull() {
    EffectRecord effect = EffectRecord.fireAndForget("SomeArchetype", Map.of("key", "value"));
    Map<String, Object> result = dispatcher.dispatch(effect);
    assertThat(result).isNull();
  }

  @Test
  void supportsClosedLoopEffect() {
    EffectRecord effect =
        new EffectRecord("Arch", Map.of(), "Effector", "Feedback", "Receptor", true);
    assertThat(dispatcher.supports(effect)).isTrue();
  }
}
