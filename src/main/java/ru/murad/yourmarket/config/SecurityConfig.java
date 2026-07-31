package ru.murad.yourmarket.config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;

@Slf4j @Configuration @RequiredArgsConstructor
public class SecurityConfig {
    private final AdminApiKeyFilter filter; private final AdminProperties properties; private final Environment environment;
    @PostConstruct void validate() {
        boolean empty=properties.apiKey()==null||properties.apiKey().isBlank();
        if(empty && Arrays.asList(environment.getActiveProfiles()).contains("production"))
            throw new IllegalStateException("ADMIN_API_KEY is required in production");
        if(empty) log.warn("Admin API key is empty; admin endpoint will reject all requests");
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf->csrf.disable()).authorizeHttpRequests(a->a.anyRequest().permitAll())
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class).build();
    }
}
