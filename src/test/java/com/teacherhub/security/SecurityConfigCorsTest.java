package com.teacherhub.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig(
            mock(JwtAuthenticationFilter.class),
            mock(ApiAuthenticationEntryPoint.class),
            mock(ApiAccessDeniedHandler.class)
    );

    @Test
    void allowsProductionFrontendOriginAndKeepsExistingFrontendOrigin() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.checkOrigin("https://www.teacherhub.co.kr"))
                .isEqualTo("https://www.teacherhub.co.kr");
        assertThat(configuration.checkOrigin("https://teacherhub-bay.vercel.app"))
                .isEqualTo("https://teacherhub-bay.vercel.app");
    }

    @Test
    void rejectsUnregisteredOrigin() {
        CorsConfiguration configuration = corsConfiguration();

        assertThat(configuration.checkOrigin("https://malicious.example"))
                .isNull();
    }

    private CorsConfiguration corsConfiguration() {
        var request = new MockHttpServletRequest("OPTIONS", "/health");
        return securityConfig.corsConfigurationSource().getCorsConfiguration(request);
    }
}
