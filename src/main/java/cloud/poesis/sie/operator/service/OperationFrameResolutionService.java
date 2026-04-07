package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.OperationFrameResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the full GSM operation frame for a Mechanism: fetches its ports
 * (receptors/effectors)
 * and their data archetypes from the Definition Manager. Validates that the
 * mechanism is wired to
 * the SIE Operator's run-operation mechanism via an Interaction (operability
 * check).
 */
@Service
public class OperationFrameResolutionService {

  private static final Logger log = LoggerFactory.getLogger(OperationFrameResolutionService.class);

  private static final Set<String> EXECUTABLE_STATUSES = Set.of("ACTIVE", "DEPRECATED");

  private final DefinitionManagerClient client;

  public OperationFrameResolutionService(DefinitionManagerClient client) {
    this.client = client;
  }

  public OperationFrameDto resolve(UUID mechanismAscriptionId) {
    MechanismAscriptionDto mechanism = client.getMechanismAscription(mechanismAscriptionId);
    if (mechanism == null) {
      throw new OperationFrameResolutionException(
          "Mechanism ascription not found: " + mechanismAscriptionId);
    }

    validateExecutableStatus(mechanism.status(), "Mechanism", mechanismAscriptionId);

    log.debug("Resolving frame for mechanism ascription {}", mechanismAscriptionId);

    List<ReceptorAscriptionDto> receptorAscriptions = client.findReceptors(mechanismAscriptionId);
    List<EffectorAscriptionDto> effectorAscriptions = client.findEffectors(mechanismAscriptionId);

    validateOperability(mechanismAscriptionId, effectorAscriptions);

    Map<UUID, ArchetypeAscriptionDto> archetypes = new HashMap<>();
    for (ReceptorAscriptionDto r : receptorAscriptions) {
      resolveArchetype(r.archetype(), archetypes);
    }
    for (EffectorAscriptionDto e : effectorAscriptions) {
      resolveArchetype(e.archetype(), archetypes);
    }

    log.info(
        "Frame resolved for mechanism ascription {}: {} receptors, {} effectors, {} archetypes",
        mechanismAscriptionId,
        receptorAscriptions.size(),
        effectorAscriptions.size(),
        archetypes.size());

    return new OperationFrameDto(mechanism, receptorAscriptions, effectorAscriptions, archetypes);
  }

  /**
   * Validates that the client mechanism is wired to the SIE Operator's
   * run-operation mechanism. A
   * valid wiring is an active Interaction from one of the mechanism's effectors
   * to the operator's
   * receptor. The operator's receptor ID is resolved on demand from the
   * Definition Manager.
   */
  private void validateOperability(
      UUID mechanismAscriptionId, List<EffectorAscriptionDto> effectors) {
    UUID operatorReceptorId = resolveOperatorReceptorId();

    for (EffectorAscriptionDto effector : effectors) {
      List<InteractionAscriptionDto> interactions = client.findActiveInteractionsForEffector(effector.id());
      for (InteractionAscriptionDto interaction : interactions) {
        if (operatorReceptorId.equals(interaction.receptor())) {
          log.debug(
              "Operability confirmed for mechanism {} via effector {} → interaction {} → "
                  + "operator receptor {}",
              mechanismAscriptionId,
              effector.id(),
              interaction.id(),
              operatorReceptorId);
          return;
        }
      }
    }

    throw new OperationFrameResolutionException(
        "Mechanism "
            + mechanismAscriptionId
            + " is not wired to the SIE Operator's run-operation mechanism — "
            + "no Interaction found from any of its effectors to the operator's receptor "
            + operatorReceptorId);
  }

  private UUID resolveOperatorReceptorId() {
    Optional<JsonNode> mechanism = client.findAscription("MECHANISM", Map.of("function", "run-operation"));
    if (mechanism.isEmpty()) {
      throw new OperationFrameResolutionException(
          "SIE Operator mechanism (function=run-operation) not found on Definition Manager");
    }
    UUID mechanismId = UUID.fromString(mechanism.get().path("id").asText());

    List<ReceptorAscriptionDto> receptors = client.findReceptors(mechanismId);
    if (receptors.isEmpty()) {
      throw new OperationFrameResolutionException(
          "SIE Operator mechanism " + mechanismId + " has no receptor");
    }
    return receptors.getFirst().id();
  }

  private void resolveArchetype(
      UUID archetypeAscriptionId, Map<UUID, ArchetypeAscriptionDto> archetypes) {
    if (!archetypes.containsKey(archetypeAscriptionId)) {
      ArchetypeAscriptionDto archetype = client.getArchetypeAscription(archetypeAscriptionId);
      archetypes.put(archetypeAscriptionId, archetype);
    }
  }

  private void validateExecutableStatus(String status, String label, UUID identifier) {
    if (!EXECUTABLE_STATUSES.contains(status)) {
      throw new OperationFrameResolutionException(
          label + " " + identifier + " has non-executable status: " + status);
    }
    if ("DEPRECATED".equals(status)) {
      log.warn("{} {} is DEPRECATED — execution allowed but sunset expected", label, identifier);
    }
  }
}
