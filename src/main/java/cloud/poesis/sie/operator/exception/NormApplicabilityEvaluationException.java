package cloud.poesis.sie.operator.exception;

public class NormApplicabilityEvaluationException extends RuntimeException {
  public NormApplicabilityEvaluationException(String message) {
    super(message);
  }

  public NormApplicabilityEvaluationException(String message, Throwable cause) {
    super(message, cause);
  }
}
