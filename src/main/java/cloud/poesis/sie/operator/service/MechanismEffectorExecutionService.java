package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import java.util.Map;

public interface MechanismEffectorExecutionService {

  boolean supports(EffectDto effect);

  Map<String, Object> dispatch(EffectDto effect);

  default Map<String, Object> dispatch(EffectDto effect, OperationFrameDto frame) {
    return dispatch(effect);
  }
}
