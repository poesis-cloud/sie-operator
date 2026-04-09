package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.SyntaxError;

public class OperationExecutionService {

  private static final long DEFAULT_MAX_STEPS = 100_000L;

  private final long maxSteps;
  private final SandboxFactory sandboxFactory;

  public OperationExecutionService(SandboxFactory sandboxFactory) {
    this(sandboxFactory, DEFAULT_MAX_STEPS);
  }

  OperationExecutionService(SandboxFactory sandboxFactory, long maxSteps) {
    this.sandboxFactory = sandboxFactory;
    this.maxSteps = maxSteps;
  }

  public ExecutionResult execute(
      String mechanismId,
      String ruleSource,
      Map<String, Object> operationInput,
      Function<EffectDto, Object> effectHandler) {
    return execute(mechanismId, ruleSource, operationInput, effectHandler, Set.of(), Set.of());
  }

  public ExecutionResult execute(
      String mechanismId,
      String ruleSource,
      Map<String, Object> operationInput,
      Function<EffectDto, Object> effectHandler,
      Set<String> validReceptorArchetypes,
      Set<String> validEffectorArchetypes) {

    RuleSandbox sandbox =
        sandboxFactory.create(
            mechanismId,
            operationInput,
            effectHandler,
            validReceptorArchetypes,
            validEffectorArchetypes);

    try (Mutability mu = Mutability.create("rule")) {
      StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
      thread.setMaxExecutionSteps(maxSteps);

      String wrapped = wrapInFunction(ruleSource);
      ParserInput input = ParserInput.fromString(wrapped, "<rule>");

      Starlark.execFile(input, FileOptions.DEFAULT, sandbox.module(), thread);

      List<EffectDto> effects = sandbox.complete().get();
      return new ExecutionResult(true, effects, null);

    } catch (SyntaxError.Exception e) {
      return new ExecutionResult(false, Collections.emptyList(), e.getMessage());
    } catch (EvalException e) {
      return new ExecutionResult(false, Collections.emptyList(), e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ExecutionResult(false, Collections.emptyList(), "Execution interrupted");
    } catch (RuntimeException e) {
      return new ExecutionResult(false, Collections.emptyList(), e.getMessage());
    }
  }

  private static String wrapInFunction(String ruleSource) {
    StringBuilder sb = new StringBuilder("def _rule():\n");
    for (String line : ruleSource.split("\n", -1)) {
      if (line.isEmpty()) {
        sb.append('\n');
      } else {
        sb.append("    ").append(line).append('\n');
      }
    }
    sb.append("_rule()\n");
    return sb.toString();
  }

  public record ExecutionResult(boolean success, List<EffectDto> effects, String error) {

    public ExecutionResult {
      effects = effects != null ? List.copyOf(effects) : List.of();
    }
  }

  @FunctionalInterface
  public interface SandboxFactory {
    RuleSandbox create(
        String mechanismId,
        Map<String, Object> operationInput,
        Function<EffectDto, Object> effectHandler,
        Set<String> validReceptorArchetypes,
        Set<String> validEffectorArchetypes);
  }

  public record RuleSandbox(Module module, Supplier<List<EffectDto>> complete) {}
}
