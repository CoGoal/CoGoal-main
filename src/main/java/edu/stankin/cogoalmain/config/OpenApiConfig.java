package edu.stankin.cogoalmain.config;

import edu.stankin.cogoalmain.config.security.CurrentUserId;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html, OpenAPI spec: /v3/api-docs.
 * Use the "Authorize" button to send a bearer token with every request.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "CoGoal Main API", version = "v1"),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
@SecurityScheme(
        name = OpenApiConfig.INTERNAL_TOKEN,
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-Internal-Token",
        description = "Service-to-service calls to /internal/**"
)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String INTERNAL_TOKEN = "internalToken";

    static {
        // Filled from the token, not sent by the client
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }
}
