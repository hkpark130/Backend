package kr.co.direa.backoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.boot.autoconfigure.security.servlet.PathRequest.toH2Console;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/health",
            "/api/available-devicelist",
            "/api/device/*",
            "/api/categories",
            "/api/departments",
            "/api/projects"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(toH2Console()))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(registry -> registry
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(toH2Console()).permitAll()
                        // TODO 인증 도입 시 아래 줄을 authenticated()로 전환
                        .anyRequest().permitAll()
                );

        // TODO JWT 필터 연동 시 아래 주석 해제하여 인증 흐름 활성화
        // http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // TODO 구현 예시: JWT 서명 검증 필터. 추후 KeyResolver 교체 후 주석 해제 예정
    // private OncePerRequestFilter jwtAuthenticationFilter() {
    //     return new OncePerRequestFilter() {
    //         @Override
    //         protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    //                 throws ServletException, IOException {
    //             String bearer = request.getHeader("Authorization");
    //             if (bearer != null && bearer.startsWith("Bearer ")) {
    //                 String token = bearer.substring(7);
    //                 // TODO: JWT 검증 로직 추가 (서명 및 만료 확인)
    //                 // Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    //                 // Authentication auth = new UsernamePasswordAuthenticationToken(principal, token, authorities);
    //                 // SecurityContextHolder.getContext().setAuthentication(auth);
    //             }
    //             filterChain.doFilter(request, response);
    //         }
    //     };
    // }
}
