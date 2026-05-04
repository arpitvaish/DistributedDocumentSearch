package org.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Document Search API")
                        .version("1.0.0")
                        .description("""
                                Multi-tenant distributed document search service.

                                **Usage:** All endpoints (except `/health`) require the `X-Tenant-ID` header.
                                Click **Authorize** and enter your tenant ID to test via Swagger UI.

                                **Search syntax:** Supports full Lucene query syntax — e.g. `title:report AND finance`, `content:annual~`, `report*`.
                                """)
                        .contact(new Contact()
                                .name("Platform Engineering")
                                .email("platform@example.com"))
                )
                .addSecurityItem(new SecurityRequirement().addList("TenantAuth"))
                .components(new Components()
                        .addSecuritySchemes("TenantAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Tenant-ID")
                                        .description("Tenant identifier for multi-tenant isolation (e.g. `tenant-a`)")
                        )
                );
    }
}
