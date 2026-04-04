package cloud.poesis.sie.operator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("SIE Operator API")
                .description(
                    "Systemic Intelligence Engine — Operator. Resolves mechanism topologies, "
                        + "executes Starlark governance rules, and dispatches effects.")
                .version("1.0.0")
                .contact(new Contact().name("Poesis").url("https://poesis.cloud"))
                .license(
                    new License()
                        .name("Business Source License 1.1")
                        .url("https://github.com/poesis-cloud/sie-operator/blob/main/LICENSE")));
  }
}
