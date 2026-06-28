package com.aiagent.chatagent.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器 — 解析 Authorization 头，提取 userId 存入 request attribute
 */
@Slf4j
@Component
public class JwtHandler implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        String auth = request.getHeader("Authorization");
        if (auth == null || auth.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        try {
            Long userId = JwtUtil.parseUserId(auth);
            request.setAttribute("userId", userId);
            return true;
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            response.setStatus(401);
            return false;
        }
    }
}
