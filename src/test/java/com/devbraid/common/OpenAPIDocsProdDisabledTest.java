package com.devbraid.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OpenAPI documentation is disabled in production")
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/none",
        "spring.datasource.username=none",
        "spring.datasource.password=none",
        "app.jwt.secret=test-secret",
        "app.encryption.key=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "server.servlet.context-path=",
        "management.server.port=0"
})
@AutoConfigureMockMvc
class OpenAPIDocsProdDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /v3/api-docs returns 404 in production")
    void apiDocs_DisabledInProd() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /swagger-ui/index.html returns 404 in production")
    void swaggerUi_DisabledInProd() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isNotFound());
    }
}
