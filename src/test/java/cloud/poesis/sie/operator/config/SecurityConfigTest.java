package cloud.poesis.sie.operator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

  @Autowired private SecurityFilterChain filterChain;

  @Autowired private MockMvc mockMvc;

  @Test
  void securityFilterChainIsConfigured() {
    assertThat(filterChain).isNotNull();
  }

  @Test
  void actuatorHealthIsAccessible() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void anyEndpointIsAccessibleWhenOauth2Disabled() throws Exception {
    mockMvc.perform(get("/api/test-endpoint")).andExpect(status().isNotFound());
  }
}
