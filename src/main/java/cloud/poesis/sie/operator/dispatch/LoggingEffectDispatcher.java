package cloud.poesis.sie.operator.dispatch;

import cloud.poesis.sie.operator.starlark.EffectRecord;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order
public class LoggingEffectDispatcher implements EffectDispatcher {

  private static final Logger log = LoggerFactory.getLogger(LoggingEffectDispatcher.class);

  @Override
  public boolean supports(EffectRecord effect) {
    return true;
  }

  @Override
  public Map<String, Object> dispatch(EffectRecord effect) {
    log.info(
        "Effect dispatched: archetype={} closedLoop={} effectorArchetype={}",
        effect.archetype(),
        effect.closedLoop(),
        effect.effectorArchetype());
    return null;
  }
}
