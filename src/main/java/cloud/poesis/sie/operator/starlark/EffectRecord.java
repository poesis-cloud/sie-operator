package cloud.poesis.sie.operator.starlark;

import java.util.Collections;
import java.util.Map;

public record EffectRecord(
    String archetype,
    Map<String, Object> data,
    String effectorArchetype,
    String feedbackArchetype,
    String feedbackReceptorArchetype,
    boolean closedLoop) {

  public EffectRecord {
    data = data != null ? Collections.unmodifiableMap(data) : Map.of();
  }

  public static EffectRecord fireAndForget(String archetype, Map<String, Object> data) {
    return new EffectRecord(archetype, data, null, null, null, false);
  }
}
