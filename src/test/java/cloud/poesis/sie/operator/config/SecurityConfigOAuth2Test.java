package cloud.poesis.sie.operator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "op.security.oauth2-login-enabled=true")
@Import(SecurityConfigOAuth2Test.MockJwtDecoderConfig.class)
class SecurityConfigOAuth2Test {

  @TestConfiguration
  static class MockJwtDecoderConfig {
    @Bean
    public JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token).header("alg", "none").claim("sub", "test-user").build();
    }
  }

  @Autowired private SecurityFilterChain filterChain;
  @Autowired private MockMvc mockMvc;

  @Test
  void securityFilterChainIsConfiguredWithOAuth2() {
    assertThat(filterChain).isNotNull();
  }

  @Test
  void actuatorHealthIsAccessibleWithOAuth2Enabled() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void protectedEndpointRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/executions")).andExpect(status().isUnauthorized());
  }
}
