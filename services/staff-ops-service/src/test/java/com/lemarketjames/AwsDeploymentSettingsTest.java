package com.lemarketjames;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the class AWS stack expects of every service (deploy/aws/README.md): the load balancer's
 * target groups check GET /health, and the task definitions name the JWT secret JWT_SHARED_SECRET.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AwsDeploymentSettingsTest {

    @Autowired MockMvc mvc;

    @Test
    void theLoadBalancerHealthCheckAnswersWithoutASession() throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        // The existing path stays, for Docker Compose and Jenkins.
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void theJwtSecretIsReadFromEitherName() throws IOException {
        assertEquals("from-jwt-secret", jwtSecretWith(Map.of("JWT_SECRET", "from-jwt-secret", "JWT_SHARED_SECRET", "other")));
        assertEquals("from-shared-secret", jwtSecretWith(Map.of("JWT_SHARED_SECRET", "from-shared-secret")));
    }

    /**
     * jwt.secret as this service's real application.properties resolves it with these environment
     * variables. Read from the source file: some test classpaths carry an application.properties of their own.
     */
    private static String jwtSecretWith(Map<String, Object> variables) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        new PropertiesPropertySourceLoader().load("application", new FileSystemResource("src/main/resources/application.properties"))
                .forEach(environment.getPropertySources()::addLast);
        return environment.getProperty("jwt.secret");
    }
}
