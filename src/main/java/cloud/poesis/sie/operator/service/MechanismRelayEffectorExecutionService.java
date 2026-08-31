package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Native causal propagation dispatcher — the Operator's fallback protocol. Accepts any effect that
 * declares an effectorArchetype but is not claimed by a network-boundary dispatcher (HTTP, Kafka).
 *
 * <p>When an Interaction graph wires the effector to a downstream mechanism, resolves and invokes
 * the downstream mechanism (operation chain). Otherwise, propagates the causal signal in-memory by
 * returning the effect's data as-is.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MechanismRelayEffectorExecutionService implements MechanismEffectorExecutionService {

  private static final Logger log =
      LoggerFactory.getLogger(MechanismRelayEffectorExecutionService.class);

  private final DefinitionManagerClient client;
  private final OperationService operationService;

  public MechanismRelayEffectorExecutionService(
      DefinitionManagerClient client, @Lazy OperationService operationService) {
    this.client = client;
    this.operationService = operationService;
  }

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

  @Override
  public Map<String, Object> dispatch(EffectDto effect, OperationFrameDto frame) {
    String effectorArchetype = effect.effectorArchetype();
    if (effectorArchetype == null || frame == null) {
      return dispatch(effect);
    }

    Optional<EffectorAscriptionDto> effector =
        frame.findEffector(effect.archetype(), effectorArchetype);
    if (effector.isEmpty()) {
      throw new IllegalStateException(
          "No effector for data Archetype '"
              + effect.archetype()
              + "' and port Archetype '"
              + effectorArchetype
              + "'");
    }

    List<InteractionAscriptionDto> interactions =
        client.findActiveInteractionsForEffector(effector.get().id());
    if (interactions.isEmpty()) {
      log.debug("No interactions for effector {} — passthrough", effector.get().id());
      return dispatch(effect);
    }
    if (interactions.size() > 1) {
      throw new IllegalStateException(
          "Effector " + effector.get().id() + " has multiple active Interactions");
    }

    InteractionAscriptionDto interaction = interactions.getFirst();
    ReceptorAscriptionDto downstream = client.getReceptorAscription(interaction.receptor());

    log.debug(
        "Relay chain: effector {} → interaction {} → downstream mechanism {}",
        effector.get().id(),
        interaction.id(),
        downstream.mechanism());

    OperationResponseDto response =
        operationService.operate(new OperationRequestDto(downstream.mechanism(), effect.data()));

    if (!response.success()) {
      throw new IllegalStateException(
          "Downstream mechanism " + downstream.mechanism() + " failed: " + response.error());
    }

    if (response.effects().isEmpty()) {
      return Map.of();
    }
    return response.effects().getFirst().data();
  }
}
