package kr.co.direa.backoffice.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.boot.autoconfigure.security.servlet.PathRequest.toH2Console;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Value("${constants.frontend}") private String frontend;
        private final boolean h2ConsoleEnabled;

        public SecurityConfig(Environment environment) {
                this.h2ConsoleEnabled = environment.acceptsProfiles(Profiles.of("local"));
        }
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/health",
            "/api/available-devicelist",
            "/api/available-devices/counts",
            "/api/device/*",
            "/api/openstack/instance-by-floating-ip",
            "/uploads/**",
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/api/categories",
            "/api/departments",
            "/api/projects",
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll();
                    registry.requestMatchers(PUBLIC_ENDPOINTS).permitAll();
                    if (h2ConsoleEnabled) {
                        registry.requestMatchers(toH2Console()).permitAll();
                    }
                    registry.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

                @Bean
                public CorsConfigurationSource corsConfigurationSource() {
                        CorsConfiguration configuration = new CorsConfiguration();
                        configuration.setAllowedOriginPatterns(List.of(frontend));
                        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                        configuration.setAllowedHeaders(List.of("*"));
                        configuration.setExposedHeaders(List.of("*"));
                        configuration.setAllowCredentials(true);

                        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                        source.registerCorsConfiguration("/**", configuration);
                        return source;
                }
}
