package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order
public class LoggingEffectDispatchService implements EffectDispatchService {

  private static final Logger log = LoggerFactory.getLogger(LoggingEffectDispatchService.class);

  @Override
  public boolean supports(EffectDto effect) {
    return true;
  }

  @Override
  public Map<String, Object> dispatch(EffectDto effect) {
    log.info(
        "Effect dispatched: archetype={} closedLoop={} effectorArchetype={}",
        effect.archetype(),
        effect.closedLoop(),
        effect.effectorArchetype());
    return null;
  }
}
