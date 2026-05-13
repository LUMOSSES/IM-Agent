package com.shanyangcode.infintechatagent.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SourceHandler sourceHandler;
    private final JwtHandler jwtHandler;

    @Value("${agent.auth.enabled:true}")
    private boolean authEnabled;

    public WebConfig(SourceHandler sourceHandler, JwtHandler jwtHandler) {
        this.sourceHandler = sourceHandler;
        this.jwtHandler = jwtHandler;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (!authEnabled) {
            return; // 独立模式，跳过所有安全拦截
        }

        // SourceHandler: 所有请求都必须来自 Gateway
        registry.addInterceptor(sourceHandler)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**");

        // JwtHandler: AI 对话接口需要登录认证
        registry.addInterceptor(jwtHandler)
                .addPathPatterns("/chat", "/streamChat", "/insert");
    }
}
