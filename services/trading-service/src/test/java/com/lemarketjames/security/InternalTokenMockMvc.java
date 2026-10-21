package com.lemarketjames.security;

import com.lemarketjames.common.security.InternalApiTokenFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * For tests that call {@code /internal/**} as orders-service does: every MockMvc request carries
 * the shared internal token (BR-02), so a test is about its endpoint rather than the credential.
 * The token is ignored on any other path. {@code @Import} it next to {@code @AutoConfigureMockMvc}.
 */
@TestConfiguration
public class InternalTokenMockMvc {

    @Bean
    MockMvcBuilderCustomizer internalToken(@Value("${internal.api-token}") String token) {
        return builder -> builder.defaultRequest(get("/").header(InternalApiTokenFilter.HEADER, token));
    }
}
