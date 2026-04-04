package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationTopologyDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.TopologyResolutionException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves the full GSM topology for a Mechanism: fetches its ports (receptors/effectors) and their
 * data archetypes from the Definition Manager.
 */
@Service
public class TopologyResolverService {

  private static final Logger log = LoggerFactory.getLogger(TopologyResolverService.class);

  private static final Set<String> EXECUTABLE_STATUSES = Set.of("ACTIVE", "DEPRECATED");

  private final DefinitionManagerClient client;

  public TopologyResolverService(DefinitionManagerClient client) {
    this.client = client;
  }

  public OperationTopologyDto resolve(UUID mechanismAscriptionId) {
    MechanismAscriptionDto mechanism = client.getMechanismAscription(mechanismAscriptionId);
    if (mechanism == null) {
      throw new TopologyResolutionException(
          "Mechanism ascription not found: " + mechanismAscriptionId);
    }

    validateExecutableStatus(mechanism.status(), "Mechanism", mechanismAscriptionId);

    log.debug("Resolving topology for mechanism ascription {}", mechanismAscriptionId);

    List<ReceptorAscriptionDto> receptorAscriptions = client.findReceptors(mechanismAscriptionId);
    List<EffectorAscriptionDto> effectorAscriptions = client.findEffectors(mechanismAscriptionId);

    Map<String, ArchetypeAscriptionDto> archetypes = new HashMap<>();

    List<OperationTopologyDto.ResolvedPort> receptors = new ArrayList<>(receptorAscriptions.size());
    for (ReceptorAscriptionDto r : receptorAscriptions) {
      receptors.add(resolvePort(r.id(), r.archetype(), archetypes));
    }

    List<OperationTopologyDto.ResolvedPort> effectors = new ArrayList<>(effectorAscriptions.size());
    for (EffectorAscriptionDto e : effectorAscriptions) {
      effectors.add(resolvePort(e.id(), e.archetype(), archetypes));
    }

    log.info(
        "Topology resolved for mechanism ascription {}: {} receptors, {} effectors, {} archetypes",
        mechanismAscriptionId,
        receptors.size(),
        effectors.size(),
        archetypes.size());

    return new OperationTopologyDto(
        mechanismAscriptionId, mechanism, receptors, effectors, archetypes);
  }

  private OperationTopologyDto.ResolvedPort resolvePort(
      UUID portAscriptionId,
      UUID archetypeAscriptionId,
      Map<String, ArchetypeAscriptionDto> archetypes) {
    ArchetypeAscriptionDto archetype = client.getArchetypeAscription(archetypeAscriptionId);

    archetypes.put(archetype.title(), archetype);

    return new OperationTopologyDto.ResolvedPort(
        portAscriptionId, archetypeAscriptionId, archetype.title(), archetype.schema());
  }

  private void validateExecutableStatus(String status, String label, UUID identifier) {
    if (!EXECUTABLE_STATUSES.contains(status)) {
      throw new TopologyResolutionException(
          label + " " + identifier + " has non-executable status: " + status);
    }
    if ("DEPRECATED".equals(status)) {
      log.warn("{} {} is DEPRECATED — execution allowed but sunset expected", label, identifier);
    }
  }
}
