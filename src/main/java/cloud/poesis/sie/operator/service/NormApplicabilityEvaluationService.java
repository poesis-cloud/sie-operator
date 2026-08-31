package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.ArchetypeBindingDto;
import cloud.poesis.sie.operator.dto.NormAscriptionDto;
import cloud.poesis.sie.operator.exception.NormApplicabilityEvaluationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.CelProtoJsonAdapter;
import dev.cel.common.ast.CelConstant;
import dev.cel.common.ast.CelExpr;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelFunctionBinding;
import dev.cel.runtime.CelRuntimeFactory;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class NormApplicabilityEvaluationService {
  private static final String REF_OVERLOAD = "ref_string";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final OperationInputValidationService inputValidation;

  public NormApplicabilityEvaluationService(OperationInputValidationService inputValidation) {
    this.inputValidation = inputValidation;
  }

  public boolean evaluate(NormAscriptionDto norm, List<ArchetypeBindingDto> observations) {
    CelAbstractSyntaxTree ast = compile(norm.applicability());
    Set<String> expressionReferences = new LinkedHashSet<>();
    collectReferences(ast.getExpr(), expressionReferences);

    Map<String, ArchetypeBindingDto> bindings = new LinkedHashMap<>();
    for (ArchetypeBindingDto observation : observations) {
      if (bindings.putIfAbsent(observation.archetype(), observation) != null) {
        throw new NormApplicabilityEvaluationException(
            "Duplicate applicability binding: " + observation.archetype());
      }
    }
    if (!bindings.keySet().equals(expressionReferences)) {
      throw new NormApplicabilityEvaluationException(
          "Applicability binding target mismatch; expected "
              + expressionReferences
              + " but received "
              + bindings.keySet());
    }

    Map<String, Map<String, Object>> values = new LinkedHashMap<>();
    for (String archetypeId : expressionReferences) {
      ArchetypeBindingDto binding = bindings.get(archetypeId);
      String schemaId = binding.schema().path("$id").asText();
      if (!archetypeId.equals(schemaId)) {
        throw new NormApplicabilityEvaluationException(
            "Applicability binding schema identity mismatch: expected '"
                + archetypeId
                + "' but received '"
                + schemaId
                + "'");
      }
      var validation = inputValidation.validate(archetypeId, binding.value(), binding.schema());
      if (!validation.isValid()) {
        throw new NormApplicabilityEvaluationException(
            "Invalid applicability binding for " + archetypeId + ": " + validation.errors());
      }
      values.put(
          archetypeId,
          MAPPER.convertValue(binding.value(), new TypeReference<Map<String, Object>>() {}));
    }

    try {
      var runtime =
          CelRuntimeFactory.standardCelRuntimeBuilder()
              .addFunctionBindings(
                  CelFunctionBinding.from(
                      REF_OVERLOAD,
                      String.class,
                      id -> {
                        Map<String, Object> value = values.get(id);
                        if (value == null) {
                          throw new IllegalArgumentException(
                              "Missing applicability binding: " + id);
                        }
                        return CelProtoJsonAdapter.adaptToJsonStructValue(value);
                      }))
              .build();
      Object result = runtime.createProgram(ast).eval();
      if (result instanceof Boolean bool) {
        return bool;
      }
      throw new NormApplicabilityEvaluationException("Applicability result is not boolean");
    } catch (NormApplicabilityEvaluationException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new NormApplicabilityEvaluationException(
          "Applicability evaluation failed: " + exception.getMessage(), exception);
    }
  }

  private static CelAbstractSyntaxTree compile(String applicability) {
    try {
      var declaration =
          CelFunctionDecl.newFunctionDeclaration(
              "ref",
              CelOverloadDecl.newGlobalOverload(REF_OVERLOAD, SimpleType.DYN, SimpleType.STRING));
      return CelCompilerFactory.standardCelCompilerBuilder()
          .addFunctionDeclarations(declaration)
          .build()
          .compile(applicability)
          .getAst();
    } catch (Exception exception) {
      throw new NormApplicabilityEvaluationException("Applicability compilation failed", exception);
    }
  }

  private static void collectReferences(CelExpr expression, Set<String> references) {
    CelExpr.ExprKind kind = expression.exprKind();
    switch (kind.getKind()) {
      case SELECT -> collectReferences(kind.select().operand(), references);
      case CALL -> {
        CelExpr.CelCall call = kind.call();
        if ("ref".equals(call.function())) {
          if (call.target().isPresent()
              || call.args().size() != 1
              || call.args().getFirst().exprKind().getKind() != CelExpr.ExprKind.Kind.CONSTANT
              || call.args().getFirst().exprKind().constant().getKind()
                  != CelConstant.Kind.STRING_VALUE) {
            throw new NormApplicabilityEvaluationException(
                "Applicability ref() requires exactly one string-literal $id argument");
          }
          references.add(call.args().getFirst().exprKind().constant().stringValue());
        }
        call.target().ifPresent(target -> collectReferences(target, references));
        for (CelExpr argument : call.args()) {
          collectReferences(argument, references);
        }
      }
      case LIST -> {
        for (CelExpr element : kind.list().elements()) {
          collectReferences(element, references);
        }
      }
      case COMPREHENSION, MAP, STRUCT ->
          throw new NormApplicabilityEvaluationException(
              "Unsupported CEL construct in applicability expression: " + kind.getKind());
      default -> {
        // Constants and identifiers do not contain nested ref() calls.
      }
    }
  }
}
