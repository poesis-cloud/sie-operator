package cloud.poesis.sie.operator.exception;

/**
 * Thrown when the operation topology cannot be resolved: mechanism not found, non-executable
 * status, or mechanism not wired to the SIE Operator's run-operation mechanism via an Interaction.
 */
public class OperationTopologyResolutionException extends RuntimeException {

  public OperationTopologyResolutionException(String message) {
    super(message);
  }
}
