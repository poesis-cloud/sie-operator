package cloud.poesis.sie.operator.dto;

import java.util.Collections;
import java.util.Map;

public record EffectDto(
    String archetype,
    Map<String, Object> data,
    String effectorArchetype,
    String feedbackArchetype,
    String feedbackReceptorArchetype,
    boolean closedLoop) {

  public EffectDto {
    data = data != null ? Collections.unmodifiableMap(data) : Map.of();
  }

  public static EffectDto fireAndForget(String archetype, Map<String, Object> data) {
    return new EffectDto(archetype, data, null, null, null, false);
  }
}
