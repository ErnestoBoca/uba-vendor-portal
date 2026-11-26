package com.ubagroup.vendorportal.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;

import com.ubagroup.vendorportal.entity.AppUser;
import com.ubagroup.vendorportal.repository.AppUserRepository;

/**
 * Login por formulario + sessao. Utilizadores vem da tabela app_user.
 * Para AD (futuro): trocar este UserDetailsService por ActiveDirectoryLdapAuthenticationProvider.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/error", "/error/**").permitAll()
                // Corporate Services: so regista/edita vendors (e os seus contactos e documentos)
                .requestMatchers(HttpMethod.POST, "/api/vendors").hasAnyRole("SYS_ADMIN", "CS_OFFICER")
                .requestMatchers(HttpMethod.PUT, "/api/vendors/*").hasAnyRole("SYS_ADMIN", "CS_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/vendors/*/contacts").hasAnyRole("SYS_ADMIN", "CS_OFFICER")
                .requestMatchers(HttpMethod.DELETE, "/api/vendors/*/contacts/*").hasAnyRole("SYS_ADMIN", "CS_OFFICER")
                // Quem regista o vendor nao o aprova
                .requestMatchers(HttpMethod.POST, "/api/vendors/*/review").hasAnyRole("SYS_ADMIN", "APPROVER", "COMPLIANCE", "RISK_TEAM")
                // Legal: so regista/edita/submete contratos (os documentos sao verificados no DocumentController)
                .requestMatchers("/contracts/new", "/contracts/*/edit").hasAnyRole("SYS_ADMIN", "LEGAL")
                .requestMatchers(HttpMethod.POST, "/api/contracts", "/api/contracts/*/submit").hasAnyRole("SYS_ADMIN", "LEGAL")
                .requestMatchers(HttpMethod.PUT, "/api/contracts/*").hasAnyRole("SYS_ADMIN", "LEGAL")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e.accessDeniedHandler((req, res, ex) -> {
                if (req.getRequestURI().startsWith("/api/")) {
                    res.setStatus(403);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    res.setCharacterEncoding("UTF-8");
                    res.getWriter().write("{\"error\":\"O seu perfil nao tem permissao para esta accao.\"}");
                } else {
                    res.sendError(403);
                }
            }))
            .formLogin(f -> f
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> {
            AppUser u = users.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));
            List<SimpleGrantedAuthority> roles = u.getRoles().stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getCode())).toList();
            UserDetails ud = User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .disabled(!u.isActive())
                .authorities(roles)
                .build();
            return ud;
        };
    }
}
