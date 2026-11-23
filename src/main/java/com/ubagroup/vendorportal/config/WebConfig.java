package com.ubagroup.vendorportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LayoutInterceptor layout;

    public WebConfig(LayoutInterceptor layout) {
        this.layout = layout;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(layout).excludePathPatterns("/api/**", "/css/**", "/js/**", "/img/**");
    }
}
