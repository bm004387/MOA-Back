package com.buc.moa.config;

import com.buc.moa.security.AccessTokenAuthenticationFilter;
import com.buc.moa.security.ApiSecurityErrorHandler;
import com.buc.moa.security.SessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/** API 인증 기본정책과 Redis Bearer 토큰 필터를 구성합니다. */
@Configuration
public class WebConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SessionManager sessionManager,
            ApiSecurityErrorHandler errorHandler) throws Exception {

        AccessTokenAuthenticationFilter tokenFilter = new AccessTokenAuthenticationFilter(sessionManager, errorHandler);

        http.csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(errors -> errors
            .authenticationEntryPoint(errorHandler)
            .accessDeniedHandler(errorHandler))
            .authorizeHttpRequests(authorize -> authorize
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.POST,
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/pin/login",
                                "/api/auth/pin/setup").permitAll()
            .requestMatchers(HttpMethod.GET,
                                "/api/auth/check-user-id",
                                "/api/msg/all").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/common-codes/refresh").permitAll()
            .requestMatchers("/api/auth/recovery/**", "/api/sms/**").permitAll()
            .anyRequest().authenticated())
            .addFilterBefore(tokenFilter, AnonymousAuthenticationFilter.class)
            .headers(Customizer.withDefaults());

        return http.build();
    }
}
