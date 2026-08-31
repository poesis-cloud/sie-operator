package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.OperationFrameResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperationService {

  private static final Logger log = LoggerFactory.getLogger(OperationService.class);

  private final OperationFrameResolutionService frameResolver;
  private final OperationInputValidationService inputValidator;
  private final OperationExecutionService sandbox;
  private final List<MechanismEffectorExecutionService> dispatchers;

  public OperationService(
      OperationFrameResolutionService frameResolver,
      OperationInputValidationService inputValidator,
      OperationExecutionService sandbox,
      List<MechanismEffectorExecutionService> dispatchers) {
    this.frameResolver = frameResolver;
    this.inputValidator = inputValidator;
    this.sandbox = sandbox;
    this.dispatchers = List.copyOf(dispatchers);
  }

  public OperationResponseDto operate(OperationRequestDto request) {
    OperationFrameDto frame;
    try {
      frame = frameResolver.resolve(request.mechanismAscriptionId());
    } catch (OperationFrameResolutionException e) {
      return OperationResponseDto.failure(e.getMessage());
    }

    Optional<String> unresolvedSchema = findUnresolvedSchema(frame);
    if (unresolvedSchema.isPresent()) {
      return OperationResponseDto.failure(unresolvedSchema.get());
    }

    String ruleSource = frame.getRuleSource();
    String mechanismId = frame.mechanism().id().toString();

    log.debug(
        "Executing mechanism {} with {} receptors, {} effectors",
        mechanismId,
        frame.receptors().size(),
        frame.effectors().size());

    // Validate trigger input against receptor schemas
    OperationInputValidationService.ValidationResult triggerValidation =
        validateOperationInput(request.operationInput(), frame);
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
            effect -> dispatchAndValidateReception(effect, frame),
            resolveReceptorArchetypeNames(frame),
            resolveEffectorArchetypeNames(frame),
            resolveReceptorPortArchetypeIds(frame),
            resolveEffectorPortArchetypeIds(frame));

    if (!result.success()) {
      log.warn("Mechanism {} execution failed: {}", mechanismId, result.error());
      return OperationResponseDto.failure(result.error());
    }

    log.debug("Mechanism {} produced {} effects", mechanismId, result.effects().size());
    return OperationResponseDto.success(result.effects());
  }

  private Optional<String> findUnresolvedSchema(OperationFrameDto frame) {
    for (ReceptorAscriptionDto receptor : frame.receptors()) {
      if (frame.findSchema(receptor.archetype()).isEmpty()) {
        return Optional.of("Unresolved receptor schema: " + receptor.archetype());
      }
    }
    for (cloud.poesis.sie.operator.dto.EffectorAscriptionDto effector : frame.effectors()) {
      if (frame.findSchema(effector.archetype()).isEmpty()) {
        return Optional.of("Unresolved effector schema: " + effector.archetype());
      }
    }
    return Optional.empty();
  }

  private OperationInputValidationService.ValidationResult validateOperationInput(
      Map<String, Object> operationInput, OperationFrameDto frame) {
    if (frame.receptors().isEmpty()) {
      log.debug("No receptors in frame — skipping trigger validation");
      return OperationInputValidationService.ValidationResult.valid();
    }

    OperationInputValidationService.ValidationResult lastFailure = null;
    for (ReceptorAscriptionDto receptor : frame.receptors()) {
      Optional<ArchetypeAscriptionDto> archetype = frame.findArchetype(receptor.archetype());
      if (archetype.isPresent()) {
        OperationInputValidationService.ValidationResult result =
            inputValidator.validate(
                archetype.get().uri(), operationInput, archetype.get().schema());
        if (result.isValid()) {
          return result;
        }
        lastFailure = result;
      }
    }
    return lastFailure != null
        ? lastFailure
        : OperationInputValidationService.ValidationResult.valid();
  }

  private Object dispatchAndValidateReception(EffectDto effect, OperationFrameDto frame) {
    validateEffectData(effect, frame);

    for (MechanismEffectorExecutionService dispatcher : dispatchers) {
      if (dispatcher.supports(effect)) {
        log.debug(
            "Dispatching effect archetype={} via {}",
            effect.archetype(),
            dispatcher.getClass().getSimpleName());
        Map<String, Object> reception = dispatcher.dispatch(effect, frame);
        if (effect.closedLoop() && reception != null) {
          validateReception(effect.feedbackArchetype(), reception, frame);
        }
        return reception;
      }
    }
    log.warn("No dispatcher found for effect archetype={}", effect.archetype());
    return null;
  }

  private void validateReception(
      String feedbackArchetype, Map<String, Object> reception, OperationFrameDto frame) {
    Optional<JsonNode> schema = frame.findSchema(feedbackArchetype);
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

  private void validateEffectData(EffectDto effect, OperationFrameDto frame) {
    String archetype = effect.archetype();
    if (archetype == null) {
      return;
    }
    Optional<JsonNode> schema = frame.findSchema(archetype);
    if (schema.isPresent()) {
      OperationInputValidationService.ValidationResult result =
          inputValidator.validate(archetype, effect.data(), schema.get());
      if (!result.isValid()) {
        throw new IllegalStateException(
            "Effect data validation failed against '" + archetype + "': " + result.errors());
      }
    }
  }

  private Set<String> resolveReceptorArchetypeNames(OperationFrameDto frame) {
    return frame.receptors().stream()
        .map(ReceptorAscriptionDto::archetype)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> resolveEffectorArchetypeNames(OperationFrameDto frame) {
    return frame.effectors().stream()
        .map(cloud.poesis.sie.operator.dto.EffectorAscriptionDto::archetype)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> resolveReceptorPortArchetypeIds(OperationFrameDto frame) {
    return frame.receptors().stream()
        .map(ReceptorAscriptionDto::portArchetypeUri)
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> resolveEffectorPortArchetypeIds(OperationFrameDto frame) {
    return frame.effectors().stream()
        .map(cloud.poesis.sie.operator.dto.EffectorAscriptionDto::portArchetypeUri)
        .collect(Collectors.toUnmodifiableSet());
  }
}
