package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dispatch.EffectDispatcher;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.OperationTopologyDto;
import cloud.poesis.sie.operator.exception.TopologyResolutionException;
import cloud.poesis.sie.operator.starlark.EffectRecord;
import cloud.poesis.sie.operator.starlark.StarlarkSandbox;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperationService {

  private static final Logger log = LoggerFactory.getLogger(OperationService.class);

  private final TopologyResolverService topologyResolver;
  private final PayloadValidatorService payloadValidator;
  private final StarlarkSandbox sandbox;
  private final List<EffectDispatcher> dispatchers;

  public OperationService(
      TopologyResolverService topologyResolver,
      PayloadValidatorService payloadValidator,
      StarlarkSandbox sandbox,
      List<EffectDispatcher> dispatchers) {
    this.topologyResolver = topologyResolver;
    this.payloadValidator = payloadValidator;
    this.sandbox = sandbox;
    this.dispatchers = List.copyOf(dispatchers);
  }

  public OperationResponseDto operate(OperationRequestDto request) {
    OperationTopologyDto topology;
    try {
      topology = topologyResolver.resolve(request.mechanismAscriptionId());
    } catch (TopologyResolutionException e) {
      return OperationResponseDto.failure(e.getMessage());
    }

    String ruleSource = topology.getRuleSource();
    String mechanismId = topology.mechanismAscriptionId().toString();

    log.debug(
        "Executing mechanism {} with {} receptors, {} effectors",
        mechanismId,
        topology.receptors().size(),
        topology.effectors().size());

    // Validate trigger payload against receptor schemas
    PayloadValidatorService.ValidationResult triggerValidation =
        validateTriggerPayload(request.triggerPayload(), topology);
    if (!triggerValidation.isValid()) {
      return OperationResponseDto.failure(
          "Trigger payload validation failed against '"
              + triggerValidation.archetypeName()
              + "': "
              + triggerValidation.errors());
    }

    StarlarkSandbox.ExecutionResult result =
        sandbox.execute(
            mechanismId,
            ruleSource,
            request.triggerPayload(),
            effect -> dispatchAndValidateReception(effect, topology));

    if (!result.success()) {
      log.warn("Mechanism {} execution failed: {}", mechanismId, result.error());
      return OperationResponseDto.failure(result.error());
    }

    log.debug("Mechanism {} produced {} effects", mechanismId, result.effects().size());
    return OperationResponseDto.success(result.effects());
  }

  private PayloadValidatorService.ValidationResult validateTriggerPayload(
      Map<String, Object> triggerPayload, OperationTopologyDto topology) {
    if (topology.receptors().isEmpty()) {
      log.debug("No receptors in topology — skipping trigger validation");
      return PayloadValidatorService.ValidationResult.valid();
    }

    for (OperationTopologyDto.ResolvedPort receptor : topology.receptors()) {
      Optional<JsonNode> schema = topology.findSchema(receptor.archetypeName());
      if (schema.isPresent()) {
        PayloadValidatorService.ValidationResult result =
            payloadValidator.validate(receptor.archetypeName(), triggerPayload, schema.get());
        if (!result.isValid()) {
          return result;
        }
      }
    }
    return PayloadValidatorService.ValidationResult.valid();
  }

  private Object dispatchAndValidateReception(EffectRecord effect, OperationTopologyDto topology) {
    for (EffectDispatcher dispatcher : dispatchers) {
      if (dispatcher.supports(effect)) {
        log.debug(
            "Dispatching effect archetype={} via {}",
            effect.archetype(),
            dispatcher.getClass().getSimpleName());
        Map<String, Object> reception = dispatcher.dispatch(effect);
        if (effect.closedLoop() && reception != null) {
          validateReception(effect.feedbackArchetype(), reception, topology);
        }
        return reception;
      }
    }
    log.warn("No dispatcher found for effect archetype={}", effect.archetype());
    return null;
  }

  private void validateReception(
      String feedbackArchetype, Map<String, Object> reception, OperationTopologyDto topology) {
    Optional<JsonNode> schema = topology.findSchema(feedbackArchetype);
    if (schema.isPresent()) {
      PayloadValidatorService.ValidationResult result =
          payloadValidator.validate(feedbackArchetype, reception, schema.get());
      if (!result.isValid()) {
        throw new IllegalStateException(
            "Closed-loop response validation failed against '"
                + feedbackArchetype
                + "': "
                + result.errors());
      }
    }
  }
}
