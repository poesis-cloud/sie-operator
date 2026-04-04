package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolved operation topology: the mechanism, its ports (effectors/receptors), their data
 * archetypes, and interaction wiring. Built by fetching the mechanism's constitutive parts from the
 * Definition Manager.
 */
public record OperationTopologyDto(
    UUID mechanismAscriptionId,
    MechanismAscriptionDto mechanism,
    List<ResolvedPort> receptors,
    List<ResolvedPort> effectors,
    Map<String, ArchetypeAscriptionDto> archetypes) {

  public OperationTopologyDto {
    receptors = receptors != null ? List.copyOf(receptors) : List.of();
    effectors = effectors != null ? List.copyOf(effectors) : List.of();
    archetypes = archetypes != null ? Map.copyOf(archetypes) : Map.of();
  }

  /** Finds the receptor whose data archetype matches the given archetype name. */
  public Optional<ResolvedPort> findReceptorByArchetypeName(String archetypeName) {
    return receptors.stream().filter(r -> archetypeName.equals(r.archetypeName())).findFirst();
  }

  /** Finds the effector whose data archetype matches the given archetype name. */
  public Optional<ResolvedPort> findEffectorByArchetypeName(String archetypeName) {
    return effectors.stream().filter(e -> archetypeName.equals(e.archetypeName())).findFirst();
  }

  /** Returns the JSON Schema for the named archetype, or empty if not resolved. */
  public Optional<JsonNode> findSchema(String archetypeName) {
    ArchetypeAscriptionDto archetype = archetypes.get(archetypeName);
    return archetype != null ? Optional.of(archetype.schema()) : Optional.empty();
  }

  public String getRuleSource() {
    return mechanism.rule();
  }

  /**
   * A resolved port (effector or receptor) with its data archetype identity and schema.
   *
   * @param portAscriptionId the port's ascription UUID
   * @param dataArchetypeAscriptionId the data archetype's ascription UUID (from port statement)
   * @param archetypeName the data archetype's title (e.g. "AppraisalTrigger")
   * @param archetypeSchema the data archetype's statement (the JSON Schema)
   */
  public record ResolvedPort(
      UUID portAscriptionId,
      UUID dataArchetypeAscriptionId,
      String archetypeName,
      JsonNode archetypeSchema) {}
}
