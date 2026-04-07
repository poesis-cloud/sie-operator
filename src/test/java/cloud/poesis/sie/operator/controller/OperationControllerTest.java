package cloud.poesis.sie.operator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.exception.OperationFrameResolutionException;
import cloud.poesis.sie.operator.service.OperationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OperationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockitoBean private OperationService operationService;

  @Test
  void executesMechanismAndReturnEffects() throws Exception {
    EffectDto effect = EffectDto.fireAndForget("OrderConfirmation", Map.of("orderId", "ORD-1"));
    when(operationService.operate(any())).thenReturn(OperationResponseDto.success(List.of(effect)));

    UUID mechanismId = UUID.randomUUID();
    Map<String, Object> body =
        Map.of(
            "mechanismAscriptionId", mechanismId.toString(),
            "operationInput", Map.of("orderId", "ORD-1"));

    mockMvc
        .perform(
            post("/api/v1/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.effects[0].archetype").value("OrderConfirmation"));
  }

  @Test
  void returnsFailureWhenMechanismNotFound() throws Exception {
    when(operationService.operate(any()))
        .thenReturn(OperationResponseDto.failure("Mechanism not found"));

    UUID mechanismId = UUID.randomUUID();
    Map<String, Object> body =
        Map.of(
            "mechanismAscriptionId", mechanismId.toString(),
            "operationInput", Map.of());

    mockMvc
        .perform(
            post("/api/v1/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error").value("Mechanism not found"));
  }

  @Test
  void acceptsEmptyOperationInput() throws Exception {
    when(operationService.operate(any())).thenReturn(OperationResponseDto.success(List.of()));

    UUID mechanismId = UUID.randomUUID();
    Map<String, Object> body =
        Map.of(
            "mechanismAscriptionId", mechanismId.toString(),
            "operationInput", Map.of());

    mockMvc
        .perform(
            post("/api/v1/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.effects").isEmpty());
  }

  @Test
  void returns422ForFrameResolutionException() throws Exception {
    when(operationService.operate(any()))
        .thenThrow(new OperationFrameResolutionException("Mechanism ascription not found: abc"));

    UUID mechanismId = UUID.randomUUID();
    Map<String, Object> body =
        Map.of(
            "mechanismAscriptionId", mechanismId.toString(),
            "operationInput", Map.of());

    mockMvc
        .perform(
            post("/api/v1/operations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.title").value("Operation frame resolution failed"))
        .andExpect(jsonPath("$.detail").value("Mechanism ascription not found: abc"));
  }
}
