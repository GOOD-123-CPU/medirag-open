package com.medirag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medirag.common.result.Result;
import com.medirag.common.result.ResultCode;
import com.medirag.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    /** 白名单路径（SPA 前端路由 + 公开 API） */
    private static final String[] WHITE_LIST = {
            "/",
            "/index.html",
            "/login",
            "/register",
            "/forgot-password",
            "/chat",
            "/chat/**",
            "/knowledge",
            "/dashboard",
            "/users",
            "/ai-config",
            "/profile",
            "/favicon.ico",
            "/assets/**",
            "/api/user/login",
            "/api/user/register",
            "/api/user/forgot-password/**",
            "/api/user/refresh-token",
            "/api/speech/ws",
            "/uploads/**",
            // Actuator 仅健康检查端点放行（info/metrics/env 等默认不启用），
            // 生产环境建议通过独立网关或 security 要求管理员认证后访问。
            "/actuator/health",
            // OpenAPI 文档（生产环境可用 springdoc.api-docs.enabled=false 关闭）
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(WHITE_LIST).permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint((request, response, ex) -> {
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        response.setStatus(401);
                        response.getWriter().write(
                                objectMapper.writeValueAsString(Result.error(ResultCode.UNAUTHORIZED))
                        );
                    })
                    .accessDeniedHandler((request, response, ex) -> {
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        response.setStatus(403);
                        response.getWriter().write(
                                objectMapper.writeValueAsString(Result.error(ResultCode.FORBIDDEN))
                        );
                    })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许的来源由环境变量控制（CORS_ALLOWED_ORIGINS，逗号分隔）。
        // 仅当显式配置为 "*" 时才放开所有来源，且此时不允许携带凭据，
        // 避免 "allowed-origin=* + allow-credentials=true" 这一不安全组合。
        String origins = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://localhost:8080");
        if ("*".equals(origins.trim())) {
            configuration.setAllowedOriginPatterns(List.of("*"));
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowedOriginPatterns(List.of(origins.split(",")));
            configuration.setAllowCredentials(true);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
