package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolved operation topology: the mechanism, its ports (effectors/receptors), their data
 * archetypes, and interaction wiring. Built by fetching the mechanism's constitutive parts from the
 * Definition Manager. Purely composed of typed ascription DTOs; derivation methods resolve
 * cross-references (e.g. archetype name/schema lookups) by joining through the archetypes map.
 */
public record OperationTopologyDto(
    MechanismAscriptionDto mechanism,
    List<ReceptorAscriptionDto> receptors,
    List<EffectorAscriptionDto> effectors,
    Map<UUID, ArchetypeAscriptionDto> archetypes) {

  public OperationTopologyDto {
    receptors = receptors != null ? List.copyOf(receptors) : List.of();
    effectors = effectors != null ? List.copyOf(effectors) : List.of();
    archetypes = archetypes != null ? Map.copyOf(archetypes) : Map.of();
  }

  /** Returns the archetype ascription for the given archetype ascription ID, or empty. */
  public Optional<ArchetypeAscriptionDto> findArchetype(UUID archetypeAscriptionId) {
    return Optional.ofNullable(archetypes.get(archetypeAscriptionId));
  }

  /** Finds the receptor whose data archetype matches the given archetype name. */
  public Optional<ReceptorAscriptionDto> findReceptorByArchetypeName(String archetypeName) {
    return receptors.stream()
        .filter(
            r -> {
              ArchetypeAscriptionDto a = archetypes.get(r.archetype());
              return a != null && archetypeName.equals(a.title());
            })
        .findFirst();
  }

  /** Finds the effector whose data archetype matches the given archetype name. */
  public Optional<EffectorAscriptionDto> findEffectorByArchetypeName(String archetypeName) {
    return effectors.stream()
        .filter(
            e -> {
              ArchetypeAscriptionDto a = archetypes.get(e.archetype());
              return a != null && archetypeName.equals(a.title());
            })
        .findFirst();
  }

  /** Returns the JSON Schema for the named archetype, or empty if not resolved. */
  public Optional<JsonNode> findSchema(String archetypeName) {
    return archetypes.values().stream()
        .filter(a -> archetypeName.equals(a.title()))
        .findFirst()
        .map(ArchetypeAscriptionDto::schema);
  }

  public String getRuleSource() {
    return mechanism.rule();
  }
}
