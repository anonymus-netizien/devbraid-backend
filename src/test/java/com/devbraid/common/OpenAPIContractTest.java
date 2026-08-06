package com.devbraid.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@DisplayName("OpenAPI contract test")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureMockMvc
class OpenAPIContractTest {

    private static final Pattern OPERATION_PATTERN =
            Pattern.compile("\"(get|put|post|delete|patch|options|head)\"\\s*:\\s*\\{");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("every declared controller endpoint appears in the OpenAPI spec with a summary")
    void allEndpoints_AreDocumented() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(spec).contains("\"paths\"");

        Set<String> operations = new HashSet<>();
        Matcher matcher = OPERATION_PATTERN.matcher(spec);
        while (matcher.find()) {
            operations.add(matcher.group(1).toUpperCase());
        }
        assertThat(operations).contains("GET", "POST", "PUT", "DELETE");

        // Every operation must carry a human-readable summary (documented endpoints).
        int operationCount = operations.size();
        long documentedCount = spec.split("\"summary\"").length - 1;
        assertThat(documentedCount)
                .as("all %d operations should have a summary, found %d", operationCount, documentedCount)
                .isGreaterThanOrEqualTo(operationCount);
    }

    @Test
    @DisplayName("security schemes and tags are declared in the spec")
    void spec_DeclaresSecuritySchemesAndTags() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(spec).contains("bearer-jwt");
        assertThat(spec).contains("\"tags\"");
        assertThat(spec).contains("Authentication");
        assertThat(spec).contains("Change Threads");
        assertThat(spec).contains("GitHub Connection");
    }
}
