package com.ubagroup.vendorportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LayoutInterceptor layout;
    private final AccessInterceptor access;

    public WebConfig(LayoutInterceptor layout, AccessInterceptor access) {
        this.layout = layout;
        this.access = access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(access).excludePathPatterns("/login", "/error", "/css/**", "/js/**", "/img/**");
        registry.addInterceptor(layout).excludePathPatterns("/api/**", "/css/**", "/js/**", "/img/**");
    }
}
