package cloud.poesis.sie.operator.controller;

import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.exception.OperationTopologyResolutionException;
import cloud.poesis.sie.operator.service.OperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@Tag(name = "Operations", description = "Execute mechanism operations against the SIE Operator")
public class OperationController {

  private final OperationService operationService;

  public OperationController(OperationService operationService) {
    this.operationService = operationService;
  }

  @PostMapping
  @Operation(
      summary = "Execute a mechanism operation",
      description =
          "Resolves the mechanism topology from the Definition Manager, validates the trigger "
              + "input against receptor archetype schemas, executes the Starlark rule, validates "
              + "effect outputs against effector archetype schemas, and returns the produced effects.")
  @ApiResponse(
      responseCode = "200",
      description = "Operation executed (check 'success' field for outcome)",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = OperationResponseDto.class)))
  public ResponseEntity<OperationResponseDto> operate(
      @Valid @RequestBody OperationRequestDto request) {
    OperationResponseDto response = operationService.operate(request);
    return ResponseEntity.ok(response);
  }

  @ExceptionHandler(OperationTopologyResolutionException.class)
  public ProblemDetail handleTopologyResolutionException(OperationTopologyResolutionException ex) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    problem.setTitle("Operation topology resolution failed");
    return problem;
  }
}
