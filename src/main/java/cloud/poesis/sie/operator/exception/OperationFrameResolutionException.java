package cloud.poesis.sie.operator.exception;

/**
 * Thrown when the operation frame cannot be resolved: mechanism not found, non-executable status,
 * or mechanism not wired to the SIE Operator's run-operation mechanism via an Interaction.
 */
public class OperationFrameResolutionException extends RuntimeException {

  public OperationFrameResolutionException(String message) {
    super(message);
  }
}
