package cloud.poesis.sie.operator.dispatch;

import cloud.poesis.sie.operator.starlark.EffectRecord;
import java.util.Map;

public interface EffectDispatcher {

  boolean supports(EffectRecord effect);

  Map<String, Object> dispatch(EffectRecord effect);
}
