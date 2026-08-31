package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolved operation frame: the mechanism, its ports (effectors/receptors), their data archetypes,
 * and interaction wiring. Built by fetching the mechanism's constitutive parts from the Definition
 * Manager. Purely composed of typed ascription DTOs; derivation methods resolve cross-references
 * (e.g. Archetype URI/schema lookups) by joining through the archetypes map.
 */
public record OperationFrameDto(
    MechanismAscriptionDto mechanism,
    List<ReceptorAscriptionDto> receptors,
    List<EffectorAscriptionDto> effectors,
    Map<UUID, ArchetypeAscriptionDto> archetypes) {

  public OperationFrameDto {
    receptors = receptors != null ? List.copyOf(receptors) : List.of();
    effectors = effectors != null ? List.copyOf(effectors) : List.of();
    archetypes = archetypes != null ? Map.copyOf(archetypes) : Map.of();
  }

  /** Returns the archetype ascription for the given archetype ascription ID, or empty. */
  public Optional<ArchetypeAscriptionDto> findArchetype(UUID archetypeAscriptionId) {
    return Optional.ofNullable(archetypes.get(archetypeAscriptionId));
  }

  /** Returns the Archetype Ascription the URI resolves to, or empty if it was not resolved. */
  public Optional<ArchetypeAscriptionDto> findArchetype(String archetypeUri) {
    return archetypes.values().stream()
        .filter(archetype -> archetypeUri.equals(archetype.uri()))
        .findFirst();
  }

  /** Finds the receptor whose data Archetype has the exact URI. */
  public Optional<ReceptorAscriptionDto> findReceptorByArchetypeId(String archetypeUri) {
    return receptors.stream()
        .filter(receptor -> archetypeUri.equals(receptor.archetype()))
        .findFirst();
  }

  /** Finds the receptor whose port Archetype has the exact URI. */
  public Optional<ReceptorAscriptionDto> findReceptorByPortArchetypeId(String archetypeUri) {
    return receptors.stream()
        .filter(receptor -> archetypeUri.equals(receptor.portArchetypeUri()))
        .findFirst();
  }

  /** Finds the effector whose data Archetype has the exact URI. */
  public Optional<EffectorAscriptionDto> findEffectorByArchetypeId(String archetypeUri) {
    return effectors.stream()
        .filter(effector -> archetypeUri.equals(effector.archetype()))
        .findFirst();
  }

  /** Finds the effector whose port Archetype has the exact URI. */
  public Optional<EffectorAscriptionDto> findEffectorByPortArchetypeId(String archetypeUri) {
    return effectors.stream()
        .filter(effector -> archetypeUri.equals(effector.portArchetypeUri()))
        .findFirst();
  }

  /** Finds the unique effector matching both exact data and port Archetype IDs. */
  public Optional<EffectorAscriptionDto> findEffector(
      String dataArchetypeId, String portArchetypeId) {
    List<EffectorAscriptionDto> matches =
        effectors.stream()
            .filter(effector -> dataArchetypeId.equals(effector.archetype()))
            .filter(effector -> portArchetypeId.equals(effector.portArchetypeUri()))
            .toList();
    if (matches.size() > 1) {
      throw new IllegalStateException(
          "Ambiguous effector for data Archetype '"
              + dataArchetypeId
              + "' and port Archetype '"
              + portArchetypeId
              + "'");
    }
    return matches.stream().findFirst();
  }

  /** Returns the JSON Schema for the exact Archetype URI. */
  public Optional<JsonNode> findSchema(String archetypeUri) {
    return findArchetype(archetypeUri).map(ArchetypeAscriptionDto::schema);
  }

  public String getRuleSource() {
    return mechanism.rule();
  }
}
