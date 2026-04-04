package cloud.poesis.sie.operator.starlark;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.SyntaxError;
import org.springframework.stereotype.Component;

@Component
public class StarlarkSandbox {

  private static final long DEFAULT_MAX_STEPS = 100_000L;

  private final long maxSteps;
  private final StarlarkHostFunctions hostFunctions;

  public StarlarkSandbox() {
    this(DEFAULT_MAX_STEPS);
  }

  public StarlarkSandbox(long maxSteps) {
    this.maxSteps = maxSteps;
    this.hostFunctions = new StarlarkHostFunctions();
  }

  public ExecutionResult execute(
      String mechanismId,
      String ruleSource,
      Map<String, Object> triggerPayload,
      Function<EffectRecord, Object> effectHandler) {

    StarlarkSysModule sys = new StarlarkSysModule(mechanismId, triggerPayload, effectHandler);

    ImmutableMap.Builder<String, Object> predeclared = ImmutableMap.builder();
    predeclared.put("sys", sys);
    Starlark.addMethods(predeclared, hostFunctions);

    Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared.build());

    try (Mutability mu = Mutability.create("rule")) {
      StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
      thread.setMaxExecutionSteps(maxSteps);

      String wrapped = wrapInFunction(ruleSource);
      ParserInput input = ParserInput.fromString(wrapped, "<rule>");
      FileOptions options = FileOptions.DEFAULT;

      Starlark.execFile(input, options, module, thread);

      sys.flushPendingEffects();

      List<EffectRecord> effects = sys.getEmittedEffects();
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

  public record ExecutionResult(boolean success, List<EffectRecord> effects, String error) {

    public ExecutionResult {
      effects = effects != null ? List.copyOf(effects) : List.of();
    }
  }
}
