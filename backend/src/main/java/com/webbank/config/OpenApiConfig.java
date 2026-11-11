package com.webbank.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "WebBank API", version = "v1", description = "Digital banking platform API"),
		security = @SecurityRequirement(name = "demoCustomer"))
@SecurityScheme(name = "demoCustomer", type = SecuritySchemeType.APIKEY, in = SecuritySchemeIn.HEADER,
		paramName = "X-Demo-Customer-Id",
		description = "Phase 1 demo identity (customer id). Replaced by Keycloak JWT in phase 2.")
class OpenApiConfig {

}
