package com.shanyangcode.infintechatagent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求来源拦截器 — 只接受来自 InfiniteChat Gateway 的请求
 */
@Slf4j
@Component
public class SourceHandler implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String header = request.getHeader("X-Request-Source");
        if (!"InfiniteChat-GateWay".equals(header)) {
            log.warn("非法请求来源: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(400);
            return false;
        }
        return true;
    }
}
