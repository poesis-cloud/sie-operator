package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.OperationTopologyDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.OperationTopologyResolutionException;
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

  private final OperationTopologyResolutionService topologyResolver;
  private final OperationInputValidationService inputValidator;
  private final OperationExecutionService sandbox;
  private final List<MechanismEffectorExecutionService> dispatchers;

  public OperationService(
      OperationTopologyResolutionService topologyResolver,
      OperationInputValidationService inputValidator,
      OperationExecutionService sandbox,
      List<MechanismEffectorExecutionService> dispatchers) {
    this.topologyResolver = topologyResolver;
    this.inputValidator = inputValidator;
    this.sandbox = sandbox;
    this.dispatchers = List.copyOf(dispatchers);
  }

  public OperationResponseDto operate(OperationRequestDto request) {
    OperationTopologyDto topology;
    try {
      topology = topologyResolver.resolve(request.mechanismAscriptionId());
    } catch (OperationTopologyResolutionException e) {
      return OperationResponseDto.failure(e.getMessage());
    }

    String ruleSource = topology.getRuleSource();
    String mechanismId = topology.mechanism().id().toString();

    log.debug(
        "Executing mechanism {} with {} receptors, {} effectors",
        mechanismId,
        topology.receptors().size(),
        topology.effectors().size());

    // Validate trigger input against receptor schemas
    OperationInputValidationService.ValidationResult triggerValidation =
        validateOperationInput(request.operationInput(), topology);
    if (!triggerValidation.isValid()) {
      return OperationResponseDto.failure(
          "Trigger input validation failed against '"
              + triggerValidation.archetypeName()
              + "': "
              + triggerValidation.errors());
    }

    OperationExecutionService.ExecutionResult result =
        sandbox.execute(
            mechanismId,
            ruleSource,
            request.operationInput(),
            effect -> dispatchAndValidateReception(effect, topology));

    if (!result.success()) {
      log.warn("Mechanism {} execution failed: {}", mechanismId, result.error());
      return OperationResponseDto.failure(result.error());
    }

    log.debug("Mechanism {} produced {} effects", mechanismId, result.effects().size());
    return OperationResponseDto.success(result.effects());
  }

  private OperationInputValidationService.ValidationResult validateOperationInput(
      Map<String, Object> operationInput, OperationTopologyDto topology) {
    if (topology.receptors().isEmpty()) {
      log.debug("No receptors in topology — skipping trigger validation");
      return OperationInputValidationService.ValidationResult.valid();
    }

    for (ReceptorAscriptionDto receptor : topology.receptors()) {
      Optional<ArchetypeAscriptionDto> archetype = topology.findArchetype(receptor.archetype());
      if (archetype.isPresent()) {
        OperationInputValidationService.ValidationResult result =
            inputValidator.validate(
                archetype.get().title(), operationInput, archetype.get().schema());
        if (!result.isValid()) {
          return result;
        }
      }
    }
    return OperationInputValidationService.ValidationResult.valid();
  }

  private Object dispatchAndValidateReception(EffectDto effect, OperationTopologyDto topology) {
    for (MechanismEffectorExecutionService dispatcher : dispatchers) {
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
      OperationInputValidationService.ValidationResult result =
          inputValidator.validate(feedbackArchetype, reception, schema.get());
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
