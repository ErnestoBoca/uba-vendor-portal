package com.ubagroup.vendorportal.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/error").permitAll()
                .anyRequest().authenticated())
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
