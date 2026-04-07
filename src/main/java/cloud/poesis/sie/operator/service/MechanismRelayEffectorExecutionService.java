package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Native causal propagation dispatcher — the Operator's fallback protocol. Accepts any effect that
 * declares an effectorArchetype but is not claimed by a network-boundary dispatcher (HTTP, Kafka).
 * Propagates the causal signal in-memory by returning the effect's data as-is.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MechanismRelayEffectorExecutionService implements MechanismEffectorExecutionService {

  private static final Logger log =
      LoggerFactory.getLogger(MechanismRelayEffectorExecutionService.class);

  @Override
  public boolean supports(EffectDto effect) {
    String archetype = effect.effectorArchetype();
    return archetype != null && !archetype.isEmpty();
  }

  @Override
  public Map<String, Object> dispatch(EffectDto effect) {
    log.debug(
        "Relay signal archetype={} effector={} → propagating causal signal in-memory",
        effect.archetype(),
        effect.effectorArchetype());
    return effect.data();
  }
}
